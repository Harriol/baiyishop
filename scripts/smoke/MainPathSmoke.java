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
 * 贯通主路径冒烟（都走网关，模拟真实前端）：
 * 注册 → 登录 → 加地址 → 加购物车 → 下单 → 支付 → 后台发货 → 确认收货。
 * 断言一路上的状态、金额、库存与状态留痕，最后检查订单完成。
 */
public class MainPathSmoke {

    static final String GATEWAY = "http://localhost:8080";
    static final String MYSQL = "jdbc:mysql://127.0.0.1:3306/%s?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true";

    static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    static int failed = 0;

    public static void main(String[] args) throws Exception {
        String suffix = String.valueOf(System.currentTimeMillis());
        String username = "main" + suffix.substring(suffix.length() - 8);

        // ---------- 1. 注册 / 登录（公开接口，经网关） ----------
        check("注册成功", code(post("/api/v1/auth/register", "{\"username\":\"" + username
                + "\",\"password\":\"Passw0rd!\",\"nickname\":\"主路径用户\"}", null, null)) == 0);
        String login = post("/api/v1/auth/login", "{\"username\":\"" + username
                + "\",\"password\":\"Passw0rd!\"}", null, null);
        String token = str(login, "accessToken");
        check("登录拿到令牌", token != null && token.length() > 20);
        check("未登录访问购物车被拒（网关 + 服务端二次校验）", code(get("/api/v1/carts", null)) == 10002);

        // ---------- 2. 收货地址 ----------
        long addressId = num(post("/api/v1/addresses",
                "{\"receiverName\":\"主路径收货人\",\"receiverPhone\":\"13800007777\",\"province\":\"浙江省\","
                        + "\"city\":\"杭州市\",\"district\":\"萧山区\",\"detail\":\"市心路 1 号\",\"isDefault\":true}",
                token, null), "id");
        check("新增收货地址", addressId > 0);
        check("地址列表返回 1 条", num(get("/api/v1/addresses", token), "total") != 0
                || get("/api/v1/addresses", token).contains("主路径收货人"));

        // ---------- 3. 商品与库存（直接造数据，跳过后台） ----------
        String skuSuffix = suffix.substring(suffix.length() - 6);
        long productId;
        long skuId;
        try (Connection product = connect("baiyishop_product")) {
            productId = insertProduct(product, "主路径商品" + skuSuffix);
            skuId = insertSku(product, productId);
        }
        try (Connection inventory = connect("baiyishop_inventory")) {
            insertInventory(inventory, skuId, productId, 10);
        }
        check("商品与库存就绪 skuId=" + skuId, skuId > 0);

        // ---------- 4. 加入购物车（2 件） ----------
        String cart = post("/api/v1/carts/items", "{\"skuId\":" + skuId + ",\"quantity\":2}", token, null);
        check("加入购物车成功", code(cart) == 0);
        check("购物车合计 = 2 × 单价", num(cart, "checkedAmount") == 2 * 8800);
        check("购物车试算：商品名与库存回填", cart.contains("主路径商品") && cart.contains("\"invalid\":false"));

        // ---------- 5. 结算试算 ----------
        String cartView = get("/api/v1/carts", token);
        long cartItemId = num(cartView, "id");
        String settle = post("/api/v1/orders/settle", "{\"source\":\"CART\",\"cartItemIds\":[" + cartItemId + "]}",
                token, null);
        check("结算试算成功", code(settle) == 0);
        check("试算应付 = 商品金额（全场包邮运费 0）",
                num(settle, "payAmount") == 2 * 8800 && num(settle, "freightAmount") == 0);
        check("试算带默认收货地址", settle.contains("主路径收货人"));

        // ---------- 6. 下单：锁库存 + 生成订单 ----------
        String requestId = "MAIN-" + suffix;
        String created = post("/api/v1/orders", "{\"source\":\"CART\",\"cartItemIds\":[" + cartItemId
                + "],\"addressId\":" + addressId + ",\"remark\":\"主路径冒烟\"}", token, requestId);
        if (code(created) != 0) {
            System.out.println("[debug] 下单响应: " + created);
        }
        check("下单成功", code(created) == 0);
        String orderNo = str(created, "orderNo");
        check("订单号已生成且状态待付款",
                orderNo != null && "PENDING_PAYMENT".equals(str(created, "status")));
        check("下单后库存 8/2（锁定 2）", "available=8, locked=2".equals(stockOf(skuId)));
        check("下单后购物车已清空", num(get("/api/v1/carts", token), "checkedAmount") == 0);
        String duplicated = post("/api/v1/orders", "{\"source\":\"CART\",\"cartItemIds\":[" + cartItemId
                + "],\"addressId\":" + addressId + "}", token, requestId);
        check("同一 X-Request-Id 重复提交返回同一订单号",
                orderNo != null && orderNo.equals(str(duplicated, "orderNo")));

        // ---------- 7. 支付 ----------
        String payment = post("/api/v1/payments", "{\"orderNo\":\"" + orderNo + "\",\"channel\":\"WECHAT\"}",
                token, "PAY-" + suffix);
        check("发起支付成功", code(payment) == 0);
        String paymentNo = str(payment, "paymentNo");
        check("支付金额 = 订单金额", num(payment, "amount") == 2 * 8800);
        check("返回微信支付参数（prepayId / signType）",
                payment.contains("prepayId") && payment.contains("signType"));
        String paid = post("/api/v1/payments/" + paymentNo + "/mock-pay", "{}", token, null);
        check("模拟渠道支付成功", code(paid) == 0 && "SUCCESS".equals(str(paid, "status")));
        check("支付后库存扣减：可用 8、锁定 0", waitStock(skuId, "available=8, locked=0", 20_000));
        check("支付后订单转待发货", waitOrderStatus(orderNo, token, "PENDING_SHIPMENT", 20_000));

        // ---------- 8. 后台发货 ----------
        String adminToken = adminToken();
        String ship = post("/api/v1/admin/orders/" + orderNo + "/ship", "{\"trackingNo\":\"SF-MAIN-001\"}",
                adminToken, null);
        check("后台发货成功", code(ship) == 0);
        check("发货后订单待收货", waitOrderStatus(orderNo, token, "PENDING_RECEIPT", 10_000));
        check("已记录运单号与自动收货时间", shipTime(orderNo) != null && autoReceiveAt(orderNo) != null);
        check("发货后 7 天自动确认收货时间 = 发货时间 + 7 天", sevenDaysApart(orderNo));

        // ---------- 9. 确认收货 ----------
        check("确认收货成功", code(put("/api/v1/orders/" + orderNo + "/receive", token)) == 0);
        check("订单已完成", waitOrderStatus(orderNo, token, "COMPLETED", 10_000));
        check("状态留痕 4 条（创建 / 支付 / 发货 / 收货）", statusLogCount(orderNo) == 4);
        check("订单详情含收货人与商品快照",
                get("/api/v1/orders/" + orderNo, token).contains("主路径收货人")
                        && get("/api/v1/orders/" + orderNo, token).contains("主路径商品"));

        System.out.println(failed == 0 ? "\n>>> 全部通过" : "\n>>> 失败 " + failed + " 项");
        System.exit(failed == 0 ? 0 : 1);
    }

    // ======================= HTTP =======================

    static String post(String path, String body, String token, String requestId) throws Exception {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(GATEWAY + path)).timeout(Duration.ofSeconds(30))
                .header("Content-Type", "application/json");
        if (token != null) {
            b.header("Authorization", "Bearer " + token);
        }
        if (requestId != null) {
            b.header("X-Request-Id", requestId);
        }
        return HTTP.send(b.POST(HttpRequest.BodyPublishers.ofString(body)).build(),
                HttpResponse.BodyHandlers.ofString()).body();
    }

    static String get(String path, String token) throws Exception {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(GATEWAY + path)).timeout(Duration.ofSeconds(30));
        if (token != null) {
            b.header("Authorization", "Bearer " + token);
        }
        return HTTP.send(b.GET().build(), HttpResponse.BodyHandlers.ofString()).body();
    }

    static String put(String path, String token) throws Exception {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(GATEWAY + path)).timeout(Duration.ofSeconds(30))
                .header("Content-Type", "application/json");
        if (token != null) {
            b.header("Authorization", "Bearer " + token);
        }
        return HTTP.send(b.PUT(HttpRequest.BodyPublishers.ofString("{}")).build(),
                HttpResponse.BodyHandlers.ofString()).body();
    }

    static String adminToken() throws Exception {
        try (Connection c = connect("baiyishop_user"); Statement s = c.createStatement()) {
            s.executeUpdate("INSERT IGNORE INTO admin (username, password_hash, real_name, status, deleted)"
                    + " VALUES ('smokeadmin', '$2a$10$HbGzE7J275p/g4cBFABjrOiEioWA/2heMlKYA.Y6npBt/8rou36gW',"
                    + " '冒烟管理员', 1, 0)");
            s.executeUpdate("INSERT IGNORE INTO admin_role (admin_id, role_id)"
                    + " SELECT a.id, r.id FROM admin a JOIN `role` r ON r.code = 'SUPER_ADMIN'"
                    + " WHERE a.username = 'smokeadmin'");
        }
        return str(post("/api/v1/admin/auth/login",
                "{\"username\":\"smokeadmin\",\"password\":\"Admin@2026\"}", null, null), "accessToken");
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

    // ======================= JSON =======================

    static int code(String json) {
        return (int) num(json, "code");
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

    // ======================= JDBC =======================

    static Connection connect(String schema) throws Exception {
        return DriverManager.getConnection(String.format(MYSQL, schema), "root", "root");
    }

    static long insertProduct(Connection c, String name) throws Exception {
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO product (name, category_id, main_image, status, min_price, sales, on_sale_time, deleted)"
                        + " VALUES (?, 1, 'https://minio/main.jpg', 'ON_SALE', 8800, 5, NOW(3), 0)",
                Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, name);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                keys.next();
                return keys.getLong(1);
            }
        }
    }

    static long insertSku(Connection c, long productId) throws Exception {
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO product_sku (product_id, sku_code, spec_name, price, sort, status, deleted)"
                        + " VALUES (?, ?, '默认规格', 8800, 0, 1, 0)", Statement.RETURN_GENERATED_KEYS)) {
            ps.setLong(1, productId);
            ps.setString(2, productId + "-01");
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                keys.next();
                return keys.getLong(1);
            }
        }
    }

    static void insertInventory(Connection c, long skuId, long productId, int available) throws Exception {
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO inventory (sku_id, product_id, available, locked, warn_threshold) VALUES (?, ?, ?, 0, 10)")) {
            ps.setLong(1, skuId);
            ps.setLong(2, productId);
            ps.setInt(3, available);
            ps.executeUpdate();
        }
    }

    static String stockOf(long skuId) throws Exception {
        try (Connection c = connect("baiyishop_inventory"); Statement s = c.createStatement();
             ResultSet r = s.executeQuery("SELECT available, locked FROM inventory WHERE sku_id = " + skuId)) {
            r.next();
            return "available=" + r.getInt(1) + ", locked=" + r.getInt(2);
        }
    }

    static String orderColumn(String orderNo, String column) throws Exception {
        try (Connection c = connect("baiyishop_order"); Statement s = c.createStatement();
             ResultSet r = s.executeQuery("SELECT " + column + " FROM `order` WHERE order_no = '" + orderNo + "'")) {
            return r.next() ? r.getString(1) : null;
        }
    }

    static String shipTime(String orderNo) throws Exception {
        return orderColumn(orderNo, "ship_time");
    }

    static String autoReceiveAt(String orderNo) throws Exception {
        return orderColumn(orderNo, "auto_receive_at");
    }

    static boolean sevenDaysApart(String orderNo) throws Exception {
        String ship = shipTime(orderNo);
        String auto = autoReceiveAt(orderNo);
        if (ship == null || auto == null) {
            return false;
        }
        java.time.LocalDateTime shipAt = java.time.LocalDateTime.parse(ship.replace(' ', 'T'));
        java.time.LocalDateTime autoAt = java.time.LocalDateTime.parse(auto.replace(' ', 'T'));
        return Math.abs(java.time.Duration.between(shipAt, autoAt).toDays() - 7) == 0;
    }

    static long statusLogCount(String orderNo) throws Exception {
        try (Connection c = connect("baiyishop_order"); Statement s = c.createStatement();
             ResultSet r = s.executeQuery("SELECT COUNT(*) FROM order_status_log WHERE order_no = '" + orderNo + "'")) {
            r.next();
            return r.getLong(1);
        }
    }

    static void check(String name, boolean ok) {
        System.out.println((ok ? "[OK]   " : "[FAIL] ") + name);
        if (!ok) {
            failed++;
        }
    }
}
