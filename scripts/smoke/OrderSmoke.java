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
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** 订单 → 库存 跨服务全局事务冒烟（Seata AT）：下单锁定、库存不足不建单、并发重复提交、取消释放。 */
public class OrderSmoke {

    static final String USER = "http://localhost:8081";
    static final String ORDER = "http://localhost:8085";
    static final String MYSQL = "jdbc:mysql://127.0.0.1:3306/%s?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true";

    static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    static int failed = 0;

    public static void main(String[] args) throws Exception {
        String suffix = String.valueOf(System.currentTimeMillis());
        String username = "smoke" + suffix.substring(suffix.length() - 8);

        String login = post(USER + "/api/v1/auth/register",
                "{\"username\":\"" + username + "\",\"password\":\"Passw0rd!\",\"nickname\":\"冒烟用户\"}", null, null)
                + post(USER + "/api/v1/auth/login",
                "{\"username\":\"" + username + "\",\"password\":\"Passw0rd!\"}", null, null);
        String token = str(login, "accessToken");
        check("登录拿到令牌", token != null && token.length() > 20);

        long addressId = num(post(USER + "/api/v1/addresses",
                "{\"receiverName\":\"冒烟收货人\",\"receiverPhone\":\"13800001111\",\"province\":\"浙江省\","
                        + "\"city\":\"杭州市\",\"district\":\"西湖区\",\"detail\":\"文一西路 100 号\",\"isDefault\":true}",
                token, null), "id");
        check("创建收货地址", addressId > 0);

        long productId;
        long skuId;
        try (Connection product = connect("baiyishop_product")) {
            productId = insertProduct(product, "冒烟商品" + suffix.substring(suffix.length() - 5));
            skuId = insertSku(product, productId);
        }
        try (Connection inventory = connect("baiyishop_inventory")) {
            insertInventory(inventory, skuId, productId, 10);
        }
        long price = unitPrice(skuId);
        check("商品与库存就绪 skuId=" + skuId + " 单价=" + price, true);

        String buyBody = "{\"source\":\"BUY_NOW\",\"skuId\":" + skuId + ",\"quantity\":%d,\"addressId\":"
                + addressId + ",\"remark\":\"冒烟\"}";

        // ---------- 1. 库存不足：40001 且不产生订单（REQ-701 约定 3） ----------
        String insufficient = post(ORDER + "/api/v1/orders", String.format(buyBody, 20), token, "SMOKE-INSUFF-" + suffix);
        check("库存不足返回 40001（实际 " + code(insufficient) + "）", code(insufficient) == 40001);
        check("库存不足后库存未变（10/0）", "available=10, locked=0".equals(stockOf(skuId)));
        check("库存不足后没有订单", orderCount(token) == 0);

        // ---------- 2. 正常下单：锁定库存 + 建单 + 延时消息 ----------
        String requestId = "SMOKE-" + suffix;
        String created = post(ORDER + "/api/v1/orders", String.format(buyBody, 3), token, requestId);
        check("下单成功", code(created) == 0);
        String orderNo = str(created, "orderNo");
        check("应付金额 = 3 × 单价（服务端计算）", num(created, "payAmount") == 3 * price);
        check("下单后库存 7/3", "available=7, locked=3".equals(stockOf(skuId)));
        check("订单已落库", orderCount(token) == 1);
        check("状态流转留痕 1 条", statusLogCount(orderNo) == 1);
        check("15 分钟延时消息已入本地消息表并投出",
                waitForOutboxStatus(orderNo, "SENT", 15_000));
        check("7 天自动收货消息暂未产生（未发货）", outboxTopicCount(orderNo, "baiyishop-order-auto-receive") == 0);

        // ---------- 3. 并发重复提交：只锁一份库存、只产生一笔订单 ----------
        String raceRequestId = "SMOKE-RACE-" + suffix;
        ExecutorService pool = Executors.newFixedThreadPool(2);
        List<Callable<String>> tasks = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
            tasks.add(() -> post(ORDER + "/api/v1/orders", String.format(buyBody, 3), token, raceRequestId));
        }
        List<Future<String>> results = pool.invokeAll(tasks);
        List<Integer> codes = new ArrayList<>();
        for (Future<String> future : results) {
            codes.add(code(future.get()));
        }
        pool.shutdown();
        check("并发重复提交都返回成功码（一个真下单、一个幂等命中）：" + codes, codes.stream().allMatch(c -> c == 0));
        // 竞态里失败方的锁库存由 Seata 依据 undo_log 回滚；最终只应剩「两笔成功订单」的量：
        // 订单 A（3）+ 竞态胜者（3）→ available=4, locked=6。若回滚失效则为 1/9
        check("并发重复提交只锁成功订单的量（Seata 已回滚失败方，实际 " + stockOf(skuId) + "）",
                "available=4, locked=6".equals(stockOf(skuId)));
        check("并发重复提交只产生一笔订单（共 2 笔）", orderCount(token) == 2);
        check("全局事务 rollback 后 undo_log 无残留（睡 3 秒后再看）", undoLogFreeAfterDelay());

        // ---------- 4. 幂等：同一 X-Request-Id 重复提交 ----------
        String again = post(ORDER + "/api/v1/orders", String.format(buyBody, 3), token, requestId);
        check("重复提交返回同一订单号", orderNo.equals(str(again, "orderNo")));
        check("重复提交不再锁库存（仍 4/6，实际 " + stockOf(skuId) + "）",
                "available=4, locked=6".equals(stockOf(skuId)));
        check("重复提交不新增订单", orderCount(token) == 2);

        // ---------- 5. 取消订单：释放锁定库存 ----------
        String cancel = put(ORDER + "/api/v1/orders/" + orderNo + "/cancel", token);
        check("取消订单成功", code(cancel) == 0);
        check("取消后库存 7/3（竞态胜者那笔仍占 3，实际 " + stockOf(skuId) + "）",
                "available=7, locked=3".equals(stockOf(skuId)));
        check("取消后状态为 CANCELLED", "CANCELLED".equals(detailStatus(orderNo, token)));
        check("状态留痕 2 条（创建 + 取消）", statusLogCount(orderNo) == 2);
        check("重复取消返回 50002（实际 " + code(put(ORDER + "/api/v1/orders/" + orderNo + "/cancel", token)) + "）",
                code(put(ORDER + "/api/v1/orders/" + orderNo + "/cancel", token)) == 50002);

        // ---------- 6. 7 天定时消息：验证 broker 的 timer 上限够用（后台发货链路由模块测试覆盖） ----------
        String delayedBizKey = "SMOKE-DELAYED-" + suffix;
        seedDelayedOutbox(delayedBizKey, "baiyishop-order-auto-receive");
        check("7 天定时消息被 broker 接受并投出（timerMaxDelaySec 生效）",
                waitForOutboxStatus(delayedBizKey, "SENT", 20_000));

        System.out.println(failed == 0 ? "\n>>> 全部通过" : "\n>>> 失败 " + failed + " 项");
        System.exit(failed == 0 ? 0 : 1);
    }

    // ======================= HTTP =======================

    static String post(String url, String body, String token, String requestId) throws Exception {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(60))
                .header("Content-Type", "application/json");
        if (token != null && !token.isBlank()) {
            b.header("Authorization", "Bearer " + token);
        }
        if (requestId != null) {
            b.header("X-Request-Id", requestId);
        }
        return send(b.POST(HttpRequest.BodyPublishers.ofString(body)).build());
    }

    static String put(String url, String token) throws Exception {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(60))
                .header("Content-Type", "application/json");
        if (token != null && !token.isBlank()) {
            b.header("Authorization", "Bearer " + token);
        }
        return send(b.PUT(HttpRequest.BodyPublishers.ofString("{}")).build());
    }

    static String get(String url, String token) throws Exception {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(60));
        if (token != null && !token.isBlank()) {
            b.header("Authorization", "Bearer " + token);
        }
        return send(b.GET().build());
    }

    static String send(HttpRequest request) throws Exception {
        return HTTP.send(request, HttpResponse.BodyHandlers.ofString()).body();
    }

    // ======================= JSON =======================

    static int code(String json) {
        return (int) num(json, "code");
    }

    static String str(String json, String key) {
        Matcher m = Pattern.compile("\"" + key + "\"\\s*:\\s*\"([^\"]*)\"").matcher(json);
        return m.find() ? m.group(1) : null;
    }

    static long num(String json, String key) {
        Matcher m = Pattern.compile("\"" + key + "\"\\s*:\\s*(-?\\d+)").matcher(json);
        return m.find() ? Long.parseLong(m.group(1)) : -1;
    }

    static String detailStatus(String orderNo, String token) throws Exception {
        return str(get(ORDER + "/api/v1/orders/" + orderNo, token), "status");
    }

    static int orderCount(String token) throws Exception {
        return (int) num(get(ORDER + "/api/v1/orders?status=ALL&page=1&size=20", token), "total");
    }

    static String latestOrderNo(String token) throws Exception {
        String body = get(ORDER + "/api/v1/orders?status=ALL&page=1&size=20", token);
        Matcher m = Pattern.compile("\"orderNo\"\\s*:\\s*\"(\\d+)\"").matcher(body);
        String latest = null;
        while (m.find()) {
            latest = m.group(1);
        }
        return latest;
    }

    // ======================= JDBC =======================

    static Connection connect(String schema) throws Exception {
        return DriverManager.getConnection(String.format(MYSQL, schema), "root", "root");
    }

    static long insertProduct(Connection c, String name) throws Exception {
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO product (name, category_id, main_image, status, min_price, sales, on_sale_time, deleted)"
                        + " VALUES (?, 1, 'https://minio/smoke.jpg', 'ON_SALE', 9900, 0, NOW(3), 0)",
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
        try (Connection c = connect("baiyishop_order");
             Statement s = c.createStatement();
             ResultSet r = s.executeQuery("SELECT COUNT(*) FROM order_status_log WHERE order_no = '" + orderNo + "'")) {
            r.next();
            return r.getLong(1);
        }
    }

    static long outboxOf(String orderNo, String status) throws Exception {
        try (Connection c = connect("baiyishop_order");
             Statement s = c.createStatement();
             ResultSet r = s.executeQuery("SELECT COUNT(*) FROM mq_outbox WHERE biz_key = '" + orderNo
                     + "' AND status = '" + status + "'")) {
            r.next();
            return r.getLong(1);
        }
    }

    static long outboxTopicCount(String orderNo, String topic) throws Exception {
        try (Connection c = connect("baiyishop_order");
             Statement s = c.createStatement();
             ResultSet r = s.executeQuery("SELECT COUNT(*) FROM mq_outbox WHERE biz_key = '" + orderNo
                     + "' AND topic = '" + topic + "'")) {
            r.next();
            return r.getLong(1);
        }
    }

    /** 直接插一条 7 天后的延时消息，验证投递任务与 broker 的定时消息能力 */
    static void seedDelayedOutbox(String bizKey, String topic) throws Exception {
        try (Connection c = connect("baiyishop_order");
             PreparedStatement ps = c.prepareStatement(
                     "INSERT INTO mq_outbox (event_id, topic, tag, biz_key, payload, status, retry_count, deliver_at)"
                             + " VALUES (?, ?, 'ORDER_AUTO_RECEIVE', ?, ?, 'PENDING', 0, DATE_ADD(NOW(3), INTERVAL 7 DAY))")) {
            ps.setString(1, bizKey);
            ps.setString(2, topic);
            ps.setString(3, bizKey);
            ps.setString(4, "{\"orderNo\":\"" + bizKey + "\"}");
            ps.executeUpdate();
        }
    }

    static boolean waitForOutboxStatus(String bizKey, String status, long timeoutMillis) throws Exception {
        long deadline = System.currentTimeMillis() + timeoutMillis;
        while (System.currentTimeMillis() < deadline) {
            if (outboxOf(bizKey, status) == 1) {
                return true;
            }
            Thread.sleep(1000);
        }
        return false;
    }

    static boolean undoLogFreeAfterDelay() throws Exception {
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
