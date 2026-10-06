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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** 支付链路冒烟：发起支付 → 渠道回调（验签 + 幂等）→ 订单转待发货 + 库存扣减。 */
public class PaySmoke {

    static final String USER = "http://localhost:8081";
    static final String ORDER = "http://localhost:8085";
    static final String PAYMENT = "http://localhost:8086";
    static final String MYSQL = "jdbc:mysql://127.0.0.1:3306/%s?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true";

    static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    static int failed = 0;

    public static void main(String[] args) throws Exception {
        String suffix = String.valueOf(System.currentTimeMillis());
        String username = "pays" + suffix.substring(suffix.length() - 8);

        post(USER + "/api/v1/auth/register", "{\"username\":\"" + username
                + "\",\"password\":\"Passw0rd!\",\"nickname\":\"支付冒烟\"}", null, null);
        String login = post(USER + "/api/v1/auth/login", "{\"username\":\"" + username
                + "\",\"password\":\"Passw0rd!\"}", null, null);
        String token = str(login, "accessToken");
        long addressId = num(post(USER + "/api/v1/addresses",
                "{\"receiverName\":\"支付收货人\",\"receiverPhone\":\"13800002222\",\"province\":\"浙江省\","
                        + "\"city\":\"杭州市\",\"district\":\"滨江区\",\"detail\":\"江南大道 1 号\",\"isDefault\":true}",
                token, null), "id");

        long productId;
        long skuId;
        try (Connection product = connect("baiyishop_product")) {
            productId = insertProduct(product, "支付冒烟商品" + suffix.substring(suffix.length() - 4));
            skuId = insertSku(product, productId);
        }
        try (Connection inventory = connect("baiyishop_inventory")) {
            insertInventory(inventory, skuId, productId, 10);
        }
        long price = unitPrice(skuId);

        String orderResp = post(ORDER + "/api/v1/orders",
                "{\"source\":\"BUY_NOW\",\"skuId\":" + skuId + ",\"quantity\":3,\"addressId\":" + addressId + "}",
                token, "PAY-SMOKE-" + suffix);
        check("下单成功", code(orderResp) == 0);
        String orderNo = str(orderResp, "orderNo");
        check("下单后库存 7/3（锁定 3）", "available=7, locked=3".equals(stockOf(skuId)));
        check("下单后订单待付款", "PENDING_PAYMENT".equals(orderStatus(orderNo, token)));

        // ---------- 1. 发起支付 ----------
        String created = post(PAYMENT + "/api/v1/payments",
                "{\"orderNo\":\"" + orderNo + "\",\"channel\":\"WECHAT\"}", token, "PAY-REQ-" + suffix);
        check("发起支付成功", code(created) == 0);
        String paymentNo = str(created, "paymentNo");
        check("支付金额与订单一致（服务端取订单金额）", num(created, "amount") == 3 * price);
        check("返回渠道支付参数", str(created, "prepayId") != null && str(created, "signType") != null);
        check("支付单待支付", "PENDING".equals(paymentStatus(paymentNo, token)));

        String again = post(PAYMENT + "/api/v1/payments",
                "{\"orderNo\":\"" + orderNo + "\",\"channel\":\"ALIPAY\"}", token, "PAY-REQ2-" + suffix);
        check("同一订单重复发起返回同一支付单", paymentNo.equals(str(again, "paymentNo")));

        // ---------- 2. 伪造回调：验签失败必须被拒 ----------
        String forged = rawPost(PAYMENT + "/api/v1/payments/callback/WECHAT",
                "{\"paymentNo\":\"" + paymentNo + "\",\"channelTradeNo\":\"FORGED-" + suffix
                        + "\",\"amount\":" + (3 * price) + ",\"status\":\"SUCCESS\"}",
                "deadbeef", null);
        check("伪造回调返回 60004", code(forged) == 60004);
        check("验签失败后支付单仍未支付", "PENDING".equals(paymentStatus(paymentNo, token)));
        check("验签失败后订单仍待付款", "PENDING_PAYMENT".equals(orderStatus(orderNo, token)));
        check("伪造回调已落审计记录", unverifiedLogs() > 0);

        // ---------- 3. 模拟渠道一键支付（内部走真实回调：验签 + 幂等） ----------
        String paid = post(PAYMENT + "/api/v1/payments/" + paymentNo + "/mock-pay", "{}", token, null);
        check("模拟支付成功", code(paid) == 0 && "SUCCESS".equals(str(paid, "status")));
        check("支付单回填渠道流水号", str(paid, "channelTradeNo") != null && str(paid, "channelTradeNo").startsWith("MOCK"));
        // 支付成功事件是异步投递的（本地消息表 → MQ → 订单消费），给它最多 20 秒
        boolean shipped = waitForOrderStatus(orderNo, token, "PENDING_SHIPMENT", 20_000);
        check("支付后订单转待发货（事件异步投递）", shipped);
        check("支付后库存扣减：可用 7、锁定 0（实际 " + stockOf(skuId) + "）", "available=7, locked=0".equals(stockOf(skuId)));
        check("状态留痕 2 条（创建 + 支付成功）", statusLogCount(orderNo) == 2);
        check("支付成功事件已入本地消息表并投出", waitForOutboxSent(orderNo, 10_000));
        check("订单已记录支付渠道与支付时间", payTypeOf(orderNo) != null && payTimeOf(orderNo) != null);

        // ---------- 4. 重复支付 / 重复回调幂等 ----------
        String paidAgain = post(PAYMENT + "/api/v1/payments/" + paymentNo + "/mock-pay", "{}", token, null);
        check("重复模拟支付幂等", code(paidAgain) == 0 && "SUCCESS".equals(str(paidAgain, "status")));
        check("重复支付不再扣库存（仍 7/0）", "available=7, locked=0".equals(stockOf(skuId)));
        check("重复支付不追加事件", outboxSent(orderNo) == 1);

        // ---------- 5. 查询 ----------
        String byNo = get(PAYMENT + "/api/v1/payments/" + paymentNo, token);
        check("按支付单查询返回成功", code(byNo) == 0 && paymentNo.equals(str(byNo, "paymentNo")));
        String byOrder = get(PAYMENT + "/api/v1/payments/by-order/" + orderNo, token);
        check("按订单查询到同一支付单", paymentNo.equals(str(byOrder, "paymentNo")));
        check("已支付订单再次发起支付被拒（60002 或复用支付单）",
                code(post(PAYMENT + "/api/v1/payments", "{\"orderNo\":\"" + orderNo
                        + "\",\"channel\":\"WECHAT\"}", token, "PAY-REQ3-" + suffix)) == 60002);

        // ---------- 6. 全局事务残留检查 ----------
        check("两库 undo_log 无残留", undoLogFree());

        System.out.println(failed == 0 ? "\n>>> 全部通过" : "\n>>> 失败 " + failed + " 项");
        System.exit(failed == 0 ? 0 : 1);
    }

    // ======================= HTTP =======================

    static String post(String url, String body, String token, String requestId) throws Exception {
        return rawPost(url, body, null, token, requestId);
    }

    static String rawPost(String url, String body, String signature, String token) throws Exception {
        return rawPost(url, body, signature, token, null);
    }

    static String rawPost(String url, String body, String signature, String token, String requestId) throws Exception {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(60))
                .header("Content-Type", "application/json");
        if (token != null && !token.isBlank()) {
            b.header("Authorization", "Bearer " + token);
        }
        if (requestId != null) {
            b.header("X-Request-Id", requestId);
        }
        if (signature != null) {
            b.header("X-Pay-Signature", signature);
        }
        HttpRequest request = body == null ? b.POST(HttpRequest.BodyPublishers.noBody()).build()
                : b.POST(HttpRequest.BodyPublishers.ofString(body)).build();
        return HTTP.send(request, HttpResponse.BodyHandlers.ofString()).body();
    }

    static String get(String url, String token) throws Exception {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(60));
        if (token != null && !token.isBlank()) {
            b.header("Authorization", "Bearer " + token);
        }
        return HTTP.send(b.GET().build(), HttpResponse.BodyHandlers.ofString()).body();
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

    static String paymentStatus(String paymentNo, String token) throws Exception {
        return str(get(PAYMENT + "/api/v1/payments/" + paymentNo, token), "status");
    }

    static String orderStatus(String orderNo, String token) throws Exception {
        return str(get(ORDER + "/api/v1/orders/" + orderNo, token), "status");
    }

    static boolean waitForOrderStatus(String orderNo, String token, String expected, long timeoutMillis)
            throws Exception {
        long deadline = System.currentTimeMillis() + timeoutMillis;
        while (System.currentTimeMillis() < deadline) {
            if (expected.equals(orderStatus(orderNo, token))) {
                return true;
            }
            Thread.sleep(1000);
        }
        return false;
    }

    static boolean waitForOutboxSent(String orderNo, long timeoutMillis) throws Exception {
        long deadline = System.currentTimeMillis() + timeoutMillis;
        while (System.currentTimeMillis() < deadline) {
            if (outboxSent(orderNo) >= 1) {
                return true;
            }
            Thread.sleep(1000);
        }
        return false;
    }

    // ======================= JDBC =======================

    static Connection connect(String schema) throws Exception {
        return DriverManager.getConnection(String.format(MYSQL, schema), "root", "root");
    }

    static long insertProduct(Connection c, String name) throws Exception {
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO product (name, category_id, main_image, status, min_price, sales, on_sale_time, deleted)"
                        + " VALUES (?, 1, 'https://minio/pay.jpg', 'ON_SALE', 6600, 0, NOW(3), 0)",
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
                        + " VALUES (?, ?, '默认规格', 6600, 0, 1, 0)", Statement.RETURN_GENERATED_KEYS)) {
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

    static long unitPrice(long skuId) throws Exception {
        try (Connection c = connect("baiyishop_product");
             Statement s = c.createStatement();
             ResultSet r = s.executeQuery("SELECT price FROM product_sku WHERE id = " + skuId)) {
            r.next();
            return r.getLong(1);
        }
    }

    static String stockOf(long skuId) throws Exception {
        try (Connection c = connect("baiyishop_inventory");
             Statement s = c.createStatement();
             ResultSet r = s.executeQuery("SELECT available, locked FROM inventory WHERE sku_id = " + skuId)) {
            r.next();
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

    static long outboxSent(String orderNo) throws Exception {
        try (Connection c = connect("baiyishop_payment"); Statement s = c.createStatement();
             ResultSet r = s.executeQuery("SELECT COUNT(*) FROM mq_outbox WHERE biz_key = '" + orderNo
                     + "' AND status = 'SENT'")) {
            r.next();
            return r.getLong(1);
        }
    }

    static long unverifiedLogs() throws Exception {
        try (Connection c = connect("baiyishop_payment"); Statement s = c.createStatement();
             ResultSet r = s.executeQuery("SELECT COUNT(*) FROM payment_callback_log WHERE sign_verified = 0")) {
            r.next();
            return r.getLong(1);
        }
    }

    static String payTypeOf(String orderNo) throws Exception {
        return orderColumn(orderNo, "pay_type");
    }

    static String payTimeOf(String orderNo) throws Exception {
        return orderColumn(orderNo, "pay_time");
    }

    static String orderColumn(String orderNo, String column) throws Exception {
        try (Connection c = connect("baiyishop_order"); Statement s = c.createStatement();
             ResultSet r = s.executeQuery("SELECT " + column + " FROM `order` WHERE order_no = '" + orderNo + "'")) {
            r.next();
            return r.getString(1);
        }
    }

    static boolean undoLogFree() throws Exception {
        Thread.sleep(3000);
        try (Connection order = connect("baiyishop_order");
             Connection inventory = connect("baiyishop_inventory");
             Statement s1 = order.createStatement();
             Statement s2 = inventory.createStatement();
             ResultSet r1 = s1.executeQuery("SELECT COUNT(*) FROM undo_log");
             ResultSet r2 = s2.executeQuery("SELECT COUNT(*) FROM undo_log")) {
            r1.next();
            r2.next();
            return r1.getLong(1) == 0 && r2.getLong(1) == 0;
        }
    }

    static void check(String name, boolean ok) {
        System.out.println((ok ? "[OK]   " : "[FAIL] ") + name);
        if (!ok) {
            failed++;
        }
    }
}
