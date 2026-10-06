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
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** 秒杀链路冒烟：后台建活动（划拨）→ Redis 预扣抢购 → MQ 异步落单 → 结果轮询 → 取消回补。 */
public class SeckillSmoke {

    static final String USER = "http://localhost:8081";
    static final String ORDER = "http://localhost:8085";
    static final String SECKILL = "http://localhost:8087";
    static final String MYSQL = "jdbc:mysql://127.0.0.1:3306/%s?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true";
    static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    static int failed = 0;

    public static void main(String[] args) throws Exception {
        String suffix = String.valueOf(System.currentTimeMillis());
        String adminToken = adminToken();
        check("后台登录拿到令牌", adminToken != null && adminToken.length() > 20);

        String username = "seks" + suffix.substring(suffix.length() - 8);
        register(username);
        String token = login(username);
        long addressId = num(post(USER + "/api/v1/addresses", addressBody(), token, null), "id");
        check("准备用户与收货地址", addressId > 0);

        long productId;
        long skuId;
        try (Connection product = connect("baiyishop_product")) {
            productId = insertProduct(product, "秒杀冒烟商品" + suffix.substring(suffix.length() - 4));
            skuId = insertSku(product, productId);
        }
        try (Connection inventory = connect("baiyishop_inventory")) {
            insertInventory(inventory, skuId, productId, 10);
        }
        check("商品与普通库存就绪 skuId=" + skuId, true);

        // ---------- 1. 后台建活动：划拨 3 件到秒杀池 ----------
        String activity = post(SECKILL + "/api/v1/admin/seckill/activities",
                "{\"name\":\"冒烟秒杀\",\"startTime\":\"" + LocalDateTime.now().minusMinutes(1).format(TIME)
                        + "\",\"endTime\":\"" + LocalDateTime.now().plusHours(2).format(TIME)
                        + "\",\"skus\":[{\"skuId\":" + skuId
                        + ",\"seckillPrice\":100,\"allocStock\":3,\"limitPerUser\":1}]}", adminToken, null);
        check("创建秒杀活动成功（返回 " + code(activity) + "）", code(activity) == 0);
        long activitySkuId = activitySkuIdOf(activity);
        check("活动含 1 个商品", activitySkuId > 0);
        check("划拨即扣减普通库存（10 → 7）", "available=7".equals(normalStockOf(skuId)));
        check("秒杀池剩余 3", poolRemaining(activitySkuId) == 3);

        // ---------- 2. 抢购：预扣成功 → 排队中 → 异步落单成功 ----------
        String requestId = "SEK-" + suffix;
        String buy = post(SECKILL + "/api/v1/seckill/activities/skus/" + activitySkuId + "/orders",
                "{\"quantity\":1,\"requestId\":\"" + requestId + "\"}", token, requestId);
        check("抢购受理成功", code(buy) == 0);
        String ticketId = str(buy, "ticketId");
        check("返回排队票据", ticketId != null && ticketId.startsWith("TK"));
        String duplicated = post(SECKILL + "/api/v1/seckill/activities/skus/" + activitySkuId + "/orders",
                "{\"quantity\":1,\"requestId\":\"" + requestId + "\"}", token, requestId);
        check("重复 requestId 返回同一票据", ticketId != null && ticketId.equals(str(duplicated, "ticketId")));
        check("同一用户超限购被拒 70005",
                code(post(SECKILL + "/api/v1/seckill/activities/skus/" + activitySkuId + "/orders",
                        "{\"quantity\":1,\"requestId\":\"SEK-2-" + suffix + "\"}", token,
                        "SEK-2-" + suffix)) == 70005);

        String result = waitResult(ticketId, token, "SUCCESS", 20_000);
        check("异步落单成功（轮询到 SUCCESS）", result != null);
        String orderNo = result == null ? null : str(result, "orderNo");
        check("结果带订单号", orderNo != null);
        check("订单来源 SECKILL 且金额=秒杀价 100",
                orderNo != null && "SECKILL".equals(orderColumn(orderNo, "source"))
                        && Long.parseLong(orderColumn(orderNo, "pay_amount")) == 100);
        check("秒杀池剩余 2（成交扣减）", poolRemaining(activitySkuId) == 2);
        check("秒杀订单不占用普通锁定库存",
                "available=7".equals(normalStockOf(skuId)) && normalLockedOf(skuId) == 0);

        // ---------- 3. 抢完剩余库存，第五位用户被拒 ----------
        for (int i = 0; i < 2; i++) {
            String otherName = "seko" + suffix.substring(suffix.length() - 6) + i;
            register(otherName);
            String otherToken = login(otherName);
            post(USER + "/api/v1/addresses", addressBody(), otherToken, null);
            check("第 " + (i + 2) + " 位用户抢购成功",
                    code(post(SECKILL + "/api/v1/seckill/activities/skus/" + activitySkuId + "/orders",
                            "{\"quantity\":1,\"requestId\":\"SEK-OTHER-" + suffix + "-" + i + "\"}", otherToken,
                            "SEK-OTHER-" + suffix + "-" + i)) == 0);
        }
        String soldOutName = "sekx" + suffix.substring(suffix.length() - 6);
        register(soldOutName);
        String soldOutToken = login(soldOutName);
        post(USER + "/api/v1/addresses", addressBody(), soldOutToken, null);
        check("库存抢完后返回 70004 售罄",
                code(post(SECKILL + "/api/v1/seckill/activities/skus/" + activitySkuId + "/orders",
                        "{\"quantity\":1,\"requestId\":\"SEK-SOLD-" + suffix + "\"}", soldOutToken,
                        "SEK-SOLD-" + suffix)) == 70004);
        // 三个用户的订单都是异步落单的，等池子真正扣到 0
        check("秒杀池剩余归零（三单全部落单）", waitPoolRemaining(activitySkuId, 0, 25_000));

        // ---------- 4. 取消秒杀订单：回补秒杀池并恢复限购（REQ-905） ----------
        check("取消订单成功", code(put(ORDER + "/api/v1/orders/" + orderNo + "/cancel", token)) == 0);
        // 先等秒杀侧把整条回补链路走完（取消消息 → 回补池子 + 恢复 Redis 计数 → 标记记录失败）
        check("票据已标记为失败（回补完成）", waitRecordFailed(ticketId, 25_000));
        check("取消后秒杀池回补 1（剩余 1）", poolRemaining(activitySkuId) == 1);
        check("取消不影响普通库存（仍 7/0）",
                "available=7".equals(normalStockOf(skuId)) && normalLockedOf(skuId) == 0);
        check("取消后同一用户可再次抢购",
                code(post(SECKILL + "/api/v1/seckill/activities/skus/" + activitySkuId + "/orders",
                        "{\"quantity\":1,\"requestId\":\"SEK-AGAIN-" + suffix + "\"}", token,
                        "SEK-AGAIN-" + suffix)) == 0);

        System.out.println(failed == 0 ? "\n>>> 全部通过" : "\n>>> 失败 " + failed + " 项");
        System.exit(failed == 0 ? 0 : 1);
    }

    // ======================= 账号与 HTTP =======================

    /** 造一个可用的后台账号并走真实登录接口拿令牌（顺带验证后台登录链路） */
    static String adminToken() throws Exception {
        try (Connection c = connect("baiyishop_user"); Statement s = c.createStatement()) {
            s.executeUpdate("INSERT IGNORE INTO admin (username, password_hash, real_name, status, deleted)"
                    + " VALUES ('smokeadmin', '$2a$10$HbGzE7J275p/g4cBFABjrOiEioWA/2heMlKYA.Y6npBt/8rou36gW',"
                    + " '冒烟管理员', 1, 0)");
            s.executeUpdate("INSERT IGNORE INTO admin_role (admin_id, role_id)"
                    + " SELECT a.id, r.id FROM admin a JOIN `role` r ON r.code = 'SUPER_ADMIN'"
                    + " WHERE a.username = 'smokeadmin'");
        }
        return str(post(USER + "/api/v1/admin/auth/login",
                "{\"username\":\"smokeadmin\",\"password\":\"Admin@2026\"}", null, null), "accessToken");
    }

    static void register(String name) throws Exception {
        post(USER + "/api/v1/auth/register", "{\"username\":\"" + name
                + "\",\"password\":\"Passw0rd!\",\"nickname\":\"秒杀用户\"}", null, null);
    }

    static String login(String name) throws Exception {
        return str(post(USER + "/api/v1/auth/login", "{\"username\":\"" + name
                + "\",\"password\":\"Passw0rd!\"}", null, null), "accessToken");
    }

    static String addressBody() {
        return "{\"receiverName\":\"秒杀收货人\",\"receiverPhone\":\"13800003333\",\"province\":\"浙江省\","
                + "\"city\":\"杭州市\",\"district\":\"余杭区\",\"detail\":\"文一西路 8 号\",\"isDefault\":true}";
    }

    static String post(String url, String body, String token, String requestId) throws Exception {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(30))
                .header("Content-Type", "application/json");
        if (token != null && !token.isBlank()) {
            b.header("Authorization", "Bearer " + token);
        }
        if (requestId != null) {
            b.header("X-Request-Id", requestId);
        }
        HttpRequest request = body == null ? b.POST(HttpRequest.BodyPublishers.noBody()).build()
                : b.POST(HttpRequest.BodyPublishers.ofString(body)).build();
        return HTTP.send(request, HttpResponse.BodyHandlers.ofString()).body();
    }

    static String put(String url, String token) throws Exception {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(30))
                .header("Content-Type", "application/json");
        if (token != null && !token.isBlank()) {
            b.header("Authorization", "Bearer " + token);
        }
        return HTTP.send(b.PUT(HttpRequest.BodyPublishers.ofString("{}")).build(),
                HttpResponse.BodyHandlers.ofString()).body();
    }

    static String get(String url, String token) throws Exception {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(30));
        if (token != null && !token.isBlank()) {
            b.header("Authorization", "Bearer " + token);
        }
        return HTTP.send(b.GET().build(), HttpResponse.BodyHandlers.ofString()).body();
    }

    static String waitResult(String ticketId, String token, String expected, long timeoutMillis) throws Exception {
        long deadline = System.currentTimeMillis() + timeoutMillis;
        while (System.currentTimeMillis() < deadline) {
            String body = get(SECKILL + "/api/v1/seckill/results/" + ticketId, token);
            if (expected.equals(str(body, "status"))) {
                return body;
            }
            Thread.sleep(500);
        }
        return null;
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

    static long activitySkuIdOf(String activityJson) {
        Matcher m = Pattern.compile("\"skus\"\\s*:\\s*\\[\\s*\\{\\s*\"id\"\\s*:\\s*(\\d+)").matcher(activityJson);
        return m.find() ? Long.parseLong(m.group(1)) : -1;
    }

    // ======================= JDBC =======================

    static Connection connect(String schema) throws Exception {
        return DriverManager.getConnection(String.format(MYSQL, schema), "root", "root");
    }

    static long insertProduct(Connection c, String name) throws Exception {
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO product (name, category_id, main_image, status, min_price, sales, on_sale_time, deleted)"
                        + " VALUES (?, 1, 'https://minio/sek.jpg', 'ON_SALE', 9900, 0, NOW(3), 0)",
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
                        + " VALUES (?, ?, '默认规格', 9900, 0, 1, 0)", Statement.RETURN_GENERATED_KEYS)) {
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

    static String normalStockOf(long skuId) throws Exception {
        try (Connection c = connect("baiyishop_inventory"); Statement s = c.createStatement();
             ResultSet r = s.executeQuery("SELECT available FROM inventory WHERE sku_id = " + skuId)) {
            r.next();
            return "available=" + r.getInt(1);
        }
    }

    static int normalLockedOf(long skuId) throws Exception {
        try (Connection c = connect("baiyishop_inventory"); Statement s = c.createStatement();
             ResultSet r = s.executeQuery("SELECT locked FROM inventory WHERE sku_id = " + skuId)) {
            r.next();
            return r.getInt(1);
        }
    }

    static int poolRemaining(long activitySkuId) throws Exception {
        try (Connection c = connect("baiyishop_inventory"); Statement s = c.createStatement();
             ResultSet r = s.executeQuery(
                     "SELECT remaining FROM seckill_stock_pool WHERE activity_sku_id = " + activitySkuId)) {
            return r.next() ? r.getInt(1) : -1;
        }
    }

    static boolean waitPoolRemaining(long activitySkuId, int expected, long timeoutMillis) throws Exception {
        long deadline = System.currentTimeMillis() + timeoutMillis;
        while (System.currentTimeMillis() < deadline) {
            if (poolRemaining(activitySkuId) == expected) {
                return true;
            }
            Thread.sleep(500);
        }
        return false;
    }

    static String orderColumn(String orderNo, String column) throws Exception {
        try (Connection c = connect("baiyishop_order"); Statement s = c.createStatement();
             ResultSet r = s.executeQuery("SELECT " + column + " FROM `order` WHERE order_no = '" + orderNo + "'")) {
            return r.next() ? r.getString(1) : null;
        }
    }

    static boolean waitRecordFailed(String ticketId, long timeoutMillis) throws Exception {
        long deadline = System.currentTimeMillis() + timeoutMillis;
        while (System.currentTimeMillis() < deadline) {
            try (Connection c = connect("baiyishop_seckill"); Statement s = c.createStatement();
                 ResultSet r = s.executeQuery("SELECT status FROM seckill_record WHERE ticket_id = '" + ticketId + "'")) {
                if (r.next() && "FAILED".equals(r.getString(1))) {
                    return true;
                }
            }
            Thread.sleep(500);
        }
        return false;
    }

    static void check(String name, boolean ok) {
        System.out.println((ok ? "[OK]   " : "[FAIL] ") + name);
        if (!ok) {
            failed++;
        }
    }
}
