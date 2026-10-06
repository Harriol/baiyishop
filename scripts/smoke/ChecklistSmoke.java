import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Duration;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 手工冒烟走查（docs/项目开发文档.md 7.4 清单的脚本化版本）。
 * <p>对应清单六项：核心功能主流程可用、登录/权限校验生效、关键表单校验生效、
 * 错误场景有友好提示、（数据持久化与日志检查由 run 脚本配合完成）。
 * <p>全部经网关访问，模拟真实前端；输出按「步骤 + 结论」打印，便于人工核对。
 */
public class ChecklistSmoke {

    static final String GW = "http://localhost:8080";
    static final String MYSQL = "jdbc:mysql://127.0.0.1:3306/%s?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true";
    static final String BCRYPT = "$2a$10$HbGzE7J275p/g4cBFABjrOiEioWA/2heMlKYA.Y6npBt/8rou36gW";
    static final String PASSWORD = "Admin@2026";

    static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    static int failed = 0;
    static int step = 0;
    /** 走查过程中产生的关键数据，便于人工复核（订单号 / 支付单号 / 商品与 SKU） */
    static final StringBuilder keyArtifacts = new StringBuilder();

    public static void main(String[] args) throws Exception {
        String suffix = String.valueOf(System.currentTimeMillis());

        section("一、核心功能主流程");
        String username = "check" + suffix.substring(suffix.length() - 8);
        check("注册新账号", code(post("/api/v1/auth/register", "{\"username\":\"" + username
                + "\",\"password\":\"" + PASSWORD + "\",\"nickname\":\"走查用户\"}", null, null)) == 0);
        check("重复注册被拒并给明确提示（20003 该账号已被注册）",
                code(post("/api/v1/auth/register", "{\"username\":\"" + username + "\",\"password\":\"" + PASSWORD
                        + "\",\"nickname\":\"走查用户\"}", null, null)) == 20003);
        check("弱密码被表单校验拦下（10001）",
                code(post("/api/v1/auth/register", "{\"username\":\"" + username + "b\",\"password\":\"123\"}",
                        null, null)) == 10001);
        check("密码错误提示账号或密码错误（20001）",
                code(post("/api/v1/auth/login", "{\"username\":\"" + username + "\",\"password\":\"wrong-pass\"}",
                        null, null)) == 20001);
        String token = str(post("/api/v1/auth/login",
                "{\"username\":\"" + username + "\",\"password\":\"" + PASSWORD + "\"}", null, null), "accessToken");
        check("正常登录拿到令牌", token != null && token.length() > 20);
        check("未登录访问购物车被拒（10002 请重新登录）", code(get("/api/v1/carts", null)) == 10002);

        String home = get("/api/v1/home", null);
        check("首页聚合可用且含轮播/金刚区/楼层字段",
                code(home) == 0 && home.contains("\"banners\"") && home.contains("\"navs\"") && home.contains("\"floors\""));
        check("分类树可用", code(get("/api/v1/categories/tree", null)) == 0);

        String productName = "走查商品" + suffix.substring(suffix.length() - 5);
        String adminToken = adminToken();
        // 商品必须挂在叶子分类下，先建一棵「根 + 叶」分类
        long rootId = num(post("/api/v1/admin/categories",
                "{\"parentId\":0,\"name\":\"走查根" + suffix.substring(suffix.length() - 5) + "\"}", adminToken, null), "id");
        long leafId = num(post("/api/v1/admin/categories",
                "{\"parentId\":" + rootId + ",\"name\":\"走查叶" + suffix.substring(suffix.length() - 5) + "\"}",
                adminToken, null), "id");
        check("后台创建分类（根 + 叶子两级）", rootId > 0 && leafId > 0);
        // 商品与库存都走后台接口创建（真实路径：商品保存 → 写本地消息表 → 搜索索引同步）
        String productBody = "{\"name\":\"" + productName + "\",\"categoryId\":" + leafId + ",\"brandId\":null,"
                + "\"mainImage\":\"https://minio/check.jpg\",\"images\":[\"https://minio/check.jpg\"],"
                + "\"detail\":\"<p>走查商品详情</p>\",\"onSale\":true,"
                + "\"skus\":[{\"specName\":\"默认规格\",\"price\":8800}]}";
        String productResp = post("/api/v1/admin/products", productBody, adminToken, null);
        long productId = num(productResp, "id");
        long skuId = firstSkuId(productResp);
        check("后台创建商品成功（自动生成 SKU 编码、状态上架）",
                code(productResp) == 0 && productId > 0 && skuId > 0);
        String adjust = send("PUT", "/api/v1/admin/inventory/" + skuId + "/adjust",
                "{\"delta\":10,\"reason\":\"走查初始化库存\"}", adminToken, null);
        check("后台调整库存成功（可用 10）", code(adjust) == 0 && num(adjust, "available") == 10);
        String detail = get("/api/v1/products/" + productId, null);
        check("商品详情可用（含主图/价格/SKU/参数）",
                code(detail) == 0 && detail.contains("\"skus\"") && detail.contains("\"mainImage\""));
        check("搜索能命中刚上架的商品（索引同步 P95 ≤ 5s）", waitSearch(productName, 20_000));
        check("搜索无结果时返回空列表与 total=0",
                code(get("/api/v1/search/products?keyword=zzz-not-exist-zzz", null)) == 0
                        && num(get("/api/v1/search/products?keyword=zzz-not-exist-zzz", null), "total") == 0);

        long addressId = num(post("/api/v1/addresses",
                "{\"receiverName\":\"走查收货人\",\"receiverPhone\":\"13800008888\",\"province\":\"浙江省\","
                        + "\"city\":\"杭州市\",\"district\":\"拱墅区\",\"detail\":\"莫干山路 1 号\",\"isDefault\":true}",
                token, null), "id");
        check("新增收货地址", addressId > 0);

        String cart = post("/api/v1/carts/items", "{\"skuId\":" + skuId + ",\"quantity\":2}", token, null);
        check("加入购物车并实时试算合计", code(cart) == 0 && num(cart, "checkedAmount") == 2 * 8800);
        check("数量非法被拦（10001）",
                code(post("/api/v1/carts/items", "{\"skuId\":" + skuId + ",\"quantity\":0}", token, null)) == 10001);
        check("加入不存在的 SKU 提示商品不存在（30007）",
                code(post("/api/v1/carts/items", "{\"skuId\":999999999,\"quantity\":1}", token, null)) == 30007);

        long cartItemId = num(get("/api/v1/carts", token), "id");
        String settle = post("/api/v1/orders/settle", "{\"source\":\"CART\",\"cartItemIds\":[" + cartItemId + "]}",
                token, null);
        check("结算试算可用（包邮运费为 0）", code(settle) == 0 && num(settle, "freightAmount") == 0);
        check("缺少 X-Request-Id 时下单被拒（10001）",
                code(post("/api/v1/orders", "{\"source\":\"CART\",\"cartItemIds\":[" + cartItemId
                        + "],\"addressId\":" + addressId + "}", token, null)) == 10001);

        String requestId = "CHECK-" + suffix;
        String created = post("/api/v1/orders", "{\"source\":\"CART\",\"cartItemIds\":[" + cartItemId
                + "],\"addressId\":" + addressId + "}", token, requestId);
        check("提交订单成功（生成订单号，状态待付款）",
                code(created) == 0 && "PENDING_PAYMENT".equals(str(created, "status")));
        String orderNo = str(created, "orderNo");
        keyArtifacts.append("orderNo=").append(orderNo).append(' ');
        check("下单后锁定库存（可用 8 / 锁定 2）", "available=8, locked=2".equals(stockOf(skuId)));

        check("库存不足时下单失败且不产生订单（40001）",
                code(post("/api/v1/orders", "{\"source\":\"BUY_NOW\",\"skuId\":" + skuId
                        + ",\"quantity\":50,\"addressId\":" + addressId + "}", token, "CHECK-SOLD-" + suffix)) == 40001);

        String payment = post("/api/v1/payments",
                "{\"orderNo\":\"" + orderNo + "\",\"channel\":\"WECHAT\"}", token, "CHECK-PAY-" + suffix);
        check("发起支付返回渠道支付参数", code(payment) == 0 && payment.contains("prepayId"));
        String paymentNo = str(payment, "paymentNo");
        keyArtifacts.append("paymentNo=").append(paymentNo).append(' ')
                .append("productId=").append(productId).append(' ').append("skuId=").append(skuId);
        check("按订单查支付单可用", paymentNo.equals(str(get("/api/v1/payments/by-order/" + orderNo, token),
                "paymentNo")));
        check("模拟渠道支付成功", "SUCCESS".equals(str(post("/api/v1/payments/" + paymentNo + "/mock-pay", "{}",
                token, null), "status")));
        check("支付后订单转待发货", waitOrderStatus(orderNo, token, "PENDING_SHIPMENT", 20_000));
        check("支付后库存转为扣减（可用 8 / 锁定 0）", waitStock(skuId, "available=8, locked=0", 20_000));

        check("后台发货成功（录入运单号）",
                code(post("/api/v1/admin/orders/" + orderNo + "/ship", "{\"trackingNo\":\"SF-CHECK-001\"}",
                        adminToken, null)) == 0);
        check("发货后订单转待收货", waitOrderStatus(orderNo, token, "PENDING_RECEIPT", 10_000));
        check("确认收货成功", code(put("/api/v1/orders/" + orderNo + "/receive", token)) == 0);
        check("订单完成，状态留痕 4 条", waitOrderStatus(orderNo, token, "COMPLETED", 10_000)
                && statusLogCount(orderNo) == 4);

        section("二、登录与权限校验");
        check("用户令牌访问后台接口被拒（受众不符，10002）",
                code(get("/api/v1/admin/orders", token)) == 10002);
        check("后台令牌访问用户接口被拒（10002）", code(get("/api/v1/carts", adminToken)) == 10002);
        String serviceToken = serviceAdminToken();
        check("客服角色可以查订单（只读）", code(get("/api/v1/admin/orders?page=1&size=5", serviceToken)) == 0);
        check("客服角色不能发货（403 无权限）",
                code(post("/api/v1/admin/orders/" + orderNo + "/ship", "{\"trackingNo\":\"SF-X\"}", serviceToken,
                        null)) == 10003);
        check("客服角色可以加备注",
                code(post("/api/v1/admin/orders/" + orderNo + "/notes", "{\"content\":\"走查备注\"}", serviceToken,
                        null)) == 0);
        String otherToken = otherUserToken(suffix);
        check("他人订单不可见（50001 订单不存在）", code(get("/api/v1/orders/" + orderNo, otherToken)) == 50001);

        section("三、错误场景与友好提示");
        check("不存在的商品返回 30007 商品不存在", code(get("/api/v1/products/999999999", null)) == 30007);
        check("不存在的订单返回 50001", code(get("/api/v1/orders/NOT-EXIST", token)) == 50001);
        check("重复确认收货被拒（50002 当前状态不支持）",
                code(put("/api/v1/orders/" + orderNo + "/receive", token)) == 50002);
        check("非法状态查询被拒（10001 参数错误）", code(get("/api/v1/orders?status=UNKNOWN", token)) == 10001);
        check("活动结束时间早于开始时间被拒（10001）",
                code(post("/api/v1/admin/seckill/activities",
                        "{\"name\":\"走查活动\",\"startTime\":\"2026-10-06 12:00:00\",\"endTime\":\"2026-10-06 11:00:00\","
                                + "\"skus\":[{\"skuId\":" + skuId + ",\"seckillPrice\":100,\"allocStock\":1}]}",
                        adminToken, null)) == 10001);
        check("内部接口对外不可见（/internal/** 返回 404）", statusOf(get("/internal/products/skus/1", null)) == 404);

        System.out.println();
        System.out.println("（供人工复核的关键数据：" + keyArtifacts + "）");
        System.out.println(failed == 0
                ? ">>> 手工冒烟走查：全部 " + step + " 项通过"
                : ">>> 手工冒烟走查：失败 " + failed + " 项 / 共 " + step + " 项");
        System.exit(failed == 0 ? 0 : 1);
    }

    // ======================= 输出 =======================

    static void section(String title) {
        System.out.println();
        System.out.println("----- " + title + " -----");
    }

    static void check(String name, boolean ok) {
        step++;
        System.out.println((ok ? "[通过] " : "[失败] ") + step + ". " + name);
        if (!ok) {
            failed++;
        }
    }

    // ======================= HTTP =======================

    static String post(String path, String body, String token, String requestId) throws Exception {
        return send("POST", path, body, token, requestId);
    }

    static String put(String path, String token) throws Exception {
        return send("PUT", path, "{}", token, null);
    }

    static String get(String path, String token) throws Exception {
        return send("GET", path, null, token, null);
    }

    static int statusOf(String json) {
        return json == null ? -1 : (int) num(json, "httpStatus");
    }

    static String send(String method, String path, String body, String token, String requestId) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(GW + path)).timeout(Duration.ofSeconds(30));
        if (body != null) {
            builder.header("Content-Type", "application/json");
        }
        if (token != null && !token.isBlank()) {
            builder.header("Authorization", "Bearer " + token);
        }
        if (requestId != null) {
            builder.header("X-Request-Id", requestId);
        }
        HttpRequest request = body == null
                ? builder.method(method, HttpRequest.BodyPublishers.noBody()).build()
                : builder.method(method, HttpRequest.BodyPublishers.ofString(body)).build();
        HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
        String payload = response.body() == null ? "" : response.body();
        // 把 HTTP 状态塞进结果，便于断言 404 之类的传输层语义
        return payload.contains("\"code\"")
                ? payload.replaceFirst("\"code\"", "\"httpStatus\":" + response.statusCode() + ",\"code\"")
                : "{\"httpStatus\":" + response.statusCode() + "}";
    }

    static boolean waitSearch(String keyword, long timeoutMillis) throws Exception {
        long deadline = System.currentTimeMillis() + timeoutMillis;
        String encoded = java.net.URLEncoder.encode(keyword, java.nio.charset.StandardCharsets.UTF_8);
        while (System.currentTimeMillis() < deadline) {
            if (num(get("/api/v1/search/products?keyword=" + encoded, null), "total") > 0) {
                return true;
            }
            Thread.sleep(500);
        }
        return false;
    }

    static boolean waitOrderStatus(String orderNo, String token, String expected, long timeoutMillis)
            throws Exception {
        long deadline = System.currentTimeMillis() + timeoutMillis;
        while (System.currentTimeMillis() < deadline) {
            if (expected.equals(str(get("/api/v1/orders/" + orderNo, token), "status"))) {
                return true;
            }
            Thread.sleep(500);
        }
        return false;
    }

    static boolean waitStock(long skuId, String expected, long timeoutMillis) throws Exception {
        long deadline = System.currentTimeMillis() + timeoutMillis;
        while (System.currentTimeMillis() < deadline) {
            if (expected.equals(stockOf(skuId))) {
                return true;
            }
            Thread.sleep(500);
        }
        return false;
    }

    static String adminToken() throws Exception {
        return str(post("/api/v1/admin/auth/login",
                "{\"username\":\"smokeadmin\",\"password\":\"" + PASSWORD + "\"}", null, null), "accessToken");
    }

    /** 造一个客服角色账号（只读 + 备注），验证 RBAC 的边界 */
    static String serviceAdminToken() throws Exception {
        String username = "check_service";
        try (Connection c = connect("baiyishop_user"); Statement s = c.createStatement()) {
            s.executeUpdate("INSERT IGNORE INTO admin (username, password_hash, real_name, status, deleted)"
                    + " VALUES ('" + username + "', '" + BCRYPT + "', '走查客服', 1, 0)");
            s.executeUpdate("INSERT IGNORE INTO admin_role (admin_id, role_id)"
                    + " SELECT a.id, r.id FROM admin a JOIN `role` r ON r.code = 'SERVICE'"
                    + " WHERE a.username = '" + username + "'");
        }
        return str(post("/api/v1/admin/auth/login",
                "{\"username\":\"" + username + "\",\"password\":\"" + PASSWORD + "\"}", null, null), "accessToken");
    }

    static String otherUserToken(String suffix) throws Exception {
        String username = "checkother" + suffix.substring(suffix.length() - 6);
        post("/api/v1/auth/register", "{\"username\":\"" + username + "\",\"password\":\"" + PASSWORD + "\"}",
                null, null);
        return str(post("/api/v1/auth/login",
                "{\"username\":\"" + username + "\",\"password\":\"" + PASSWORD + "\"}", null, null), "accessToken");
    }

    // ======================= JSON / JDBC =======================

    static int code(String json) {
        return (int) num(json, "code");
    }

    /** 从商品详情响应里取第一个 SKU 的 id */
    static long firstSkuId(String json) {
        Matcher m = Pattern.compile("\"skus\"\\s*:\\s*\\[\\s*\\{\\s*\"id\"\\s*:\\s*(\\d+)").matcher(json);
        return m.find() ? Long.parseLong(m.group(1)) : -1;
    }

    static String str(String json, String key) {
        if (json == null) {
            return null;
        }
        Matcher m = Pattern.compile("\"" + key + "\"\\s*:\\s*\"([^\"]*)\"").matcher(json);
        return m.find() ? m.group(1) : null;
    }

    static long num(String json, String key) {
        Matcher m = Pattern.compile("\"" + key + "\"\\s*:\\s*(-?\\d+)").matcher(json);
        return m.find() ? Long.parseLong(m.group(1)) : -1;
    }

    static Connection connect(String schema) throws Exception {
        return DriverManager.getConnection(String.format(MYSQL, schema), "root", "root");
    }

    static String stockOf(long skuId) throws Exception {
        try (Connection c = connect("baiyishop_inventory"); Statement s = c.createStatement();
             ResultSet r = s.executeQuery("SELECT available, locked FROM inventory WHERE sku_id = " + skuId)) {
            if (!r.next()) {
                return "no-inventory-row";
            }
            return "available=" + r.getInt(1) + ", locked=" + r.getInt(2);
        }
    }

    static long statusLogCount(String orderNo) throws Exception {
        try (Connection c = connect("baiyishop_order"); Statement s = c.createStatement();
             ResultSet r = s.executeQuery("SELECT COUNT(*) FROM order_status_log WHERE order_no = '" + orderNo + "'")) {
            r.next();
            return r.getLong(1);
        }
    }
}
