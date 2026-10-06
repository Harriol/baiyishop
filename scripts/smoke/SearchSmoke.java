import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
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
 * 搜索链路冒烟：商品变更写本地消息表 → 投递 RocketMQ → search 消费 → 写入 ES → 检索命中。
 * 另外验证「全量重建索引」与「下架商品不可搜到」。
 */
public class SearchSmoke {

    static final String PRODUCT = "http://localhost:8082";
    static final String SEARCH = "http://localhost:8083";
    static final String ES = "http://localhost:9200";
    static final String MYSQL = "jdbc:mysql://127.0.0.1:3306/%s?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true";

    static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    static int failed = 0;

    public static void main(String[] args) throws Exception {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        String name = "冒烟搜索商品" + suffix;

        long productId;
        long skuId;
        try (Connection product = connect("baiyishop_product")) {
            productId = insertProduct(product, name);
            skuId = insertSku(product, productId);
        }
        check("商品与 SKU 就绪 id=" + productId, skuId > 0);

        // 商品变更 → 本地消息表（真实链路由 product-service 写，这里直接造一条等价消息）
        enqueueProductChanged(productId);
        check("变更消息已入本地消息表", outboxPending(productId) >= 1);

        // 等 product 的投递任务 + search 消费 + ES 刷新
        String hit = waitSearch(name, suffix, 30_000);
        check("索引同步完成，搜索结果命中（关键词=" + name + "）", hit != null);
        check("结果带主图与价格", hit != null && hit.contains("mainImage") && hit.contains("price"));

        // 下架商品不应被搜到：把商品置为下架并再发一条变更
        offSale(productId);
        enqueueProductChanged(productId);
        boolean hidden = waitSearchEmpty(name, suffix, 30_000);
        check("下架商品从搜索结果中消失", hidden);

        // 全量重建：应包含已下架商品（由搜索侧过滤），别名切换后仍能查
        String reindex = post(SEARCH + "/internal/search/reindex", "{}", "application/json");
        check("全量重建返回成功", code(reindex) == 0);
        check("重建写入文档数 > 0", num(reindex, "indexed") > 0);
        check("重建后索引名带时间戳（临时索引 + 别名切换）",
                str(reindex, "index") != null && str(reindex, "index").startsWith("baiyishop_product_"));
        check("重建后仍能按关键词查询（下架仍不可见）", waitSearchEmpty(name, suffix, 15_000));

        System.out.println(failed == 0 ? "\n>>> 全部通过" : "\n>>> 失败 " + failed + " 项");
        System.exit(failed == 0 ? 0 : 1);
    }

    // ======================= HTTP =======================

    static String post(String url, String body, String contentType) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(60))
                .header("Content-Type", contentType).POST(HttpRequest.BodyPublishers.ofString(body)).build();
        return HTTP.send(request, HttpResponse.BodyHandlers.ofString()).body();
    }

    static String search(String keyword) throws Exception {
        String url = SEARCH + "/api/v1/search/products?keyword=" + java.net.URLEncoder.encode(keyword,
                StandardCharsets.UTF_8) + "&size=20";
        return HTTP.send(HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(20)).GET().build(),
                HttpResponse.BodyHandlers.ofString()).body();
    }

    static String waitSearch(String keyword, String suffix, long timeoutMillis) throws Exception {
        long deadline = System.currentTimeMillis() + timeoutMillis;
        while (System.currentTimeMillis() < deadline) {
            String body = search(keyword);
            if (code(body) == 0 && num(body, "total") > 0) {
                return body;
            }
            Thread.sleep(1000);
        }
        return null;
    }

    static boolean waitSearchEmpty(String keyword, String suffix, long timeoutMillis) throws Exception {
        long deadline = System.currentTimeMillis() + timeoutMillis;
        while (System.currentTimeMillis() < deadline) {
            String body = search(keyword);
            if (code(body) == 0 && num(body, "total") == 0) {
                return true;
            }
            Thread.sleep(1000);
        }
        return false;
    }

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

    // ======================= JDBC =======================

    static Connection connect(String schema) throws Exception {
        return DriverManager.getConnection(String.format(MYSQL, schema), "root", "root");
    }

    static long insertProduct(Connection c, String name) throws Exception {
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO product (name, category_id, main_image, status, min_price, sales, on_sale_time, deleted)"
                        + " VALUES (?, 1, 'https://minio/search.jpg', 'ON_SALE', 8800, 12, NOW(3), 0)",
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

    static void offSale(long productId) throws Exception {
        try (Connection c = connect("baiyishop_product"); Statement s = c.createStatement()) {
            s.executeUpdate("UPDATE product SET status = 'OFF_SALE' WHERE id = " + productId);
        }
    }

    /** 造一条与 product-service 同构的商品变更消息（真实链路由服务写入，这里只驱动下半段） */
    static void enqueueProductChanged(long productId) throws Exception {
        String eventId = UUID.randomUUID().toString().replace("-", "");
        String payload = "{\"eventId\":\"" + eventId + "\",\"productId\":" + productId
                + ",\"action\":\"UPSERT\",\"occurredAt\":\"" + java.time.LocalDateTime.now() + "\"}";
        try (Connection c = connect("baiyishop_product");
             PreparedStatement ps = c.prepareStatement(
                     "INSERT INTO mq_outbox (event_id, topic, tag, biz_key, payload, status, retry_count)"
                             + " VALUES (?, 'baiyishop-product-changed', 'UPSERT', ?, ?, 'PENDING', 0)")) {
            ps.setString(1, eventId);
            ps.setString(2, String.valueOf(productId));
            ps.setString(3, payload);
            ps.executeUpdate();
        }
    }

    static long outboxPending(long productId) throws Exception {
        try (Connection c = connect("baiyishop_product"); Statement s = c.createStatement();
             ResultSet r = s.executeQuery("SELECT COUNT(*) FROM mq_outbox WHERE biz_key = '" + productId + "'")) {
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