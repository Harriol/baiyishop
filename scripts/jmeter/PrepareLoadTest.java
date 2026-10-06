import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * 压测数据准备（第 2 步：性能压测）。
 * <p>做四件事：
 * 1. 造 1000 个压测用户 + 默认收货地址（让秒杀订单能真正落到「待付款」，从而验证不超卖）
 * 2. 按令牌格式现签 1000 个用户令牌（不经过注册接口，省时间；签名与 JwtTokenProvider 一致）
 * 3. 造一个秒杀活动：普通库存 2000 → 划拨 1000 到秒杀池，每人限购 1
 * 4. 导出基线压测用的商品 id 列表
 * <p>输出目录：%TEMP%\baiyishop-loadtest（tokens.csv / products.csv / loadtest.properties）
 */
public class PrepareLoadTest {

    static final String GATEWAY = "http://localhost:8080";
    static final String MYSQL = "jdbc:mysql://127.0.0.1:3306/%s?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true";
    static final String OUT_DIR = System.getenv("TEMP") + "\\baiyishop-loadtest";
    static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    /** BCrypt("Admin@2026") 的固定哈希：压测用户与冒烟管理员共用这个口令，省去每次生成哈希 */
    static final String BCRYPT = "$2a$10$HbGzE7J275p/g4cBFABjrOiEioWA/2heMlKYA.Y6npBt/8rou36gW";
    static final String PASSWORD = "Admin@2026";

    static final int USERS = Integer.parseInt(System.getProperty("users", "1000"));
    static final int ALLOC_STOCK = Integer.parseInt(System.getProperty("stock", "1000"));

    public static void main(String[] args) throws Exception {
        Files.createDirectories(Path.of(OUT_DIR));
        List<long[]> users = ensureUsers(USERS);
        int tokens = loginAndWriteTokens(users);
        System.out.println("用户与令牌就绪：" + tokens + " 个（走真实登录接口）");

        List<Long> products = onSaleProductIds(50);
        writeProducts(products);
        System.out.println("基线商品 id：" + products.size() + " 个");

        long activitySkuId = createSeckillActivity();
        writeProperties(activitySkuId, products.size());
        System.out.println("秒杀活动就绪：activitySkuId=" + activitySkuId + " 划拨=" + ALLOC_STOCK);
        System.out.println("输出目录：" + OUT_DIR);
    }

    /** 造 / 复用压测用户，返回 [userId, addressId] 列表 */
    static List<long[]> ensureUsers(int count) throws Exception {
        List<long[]> result = new ArrayList<>();
        try (Connection c = connect("baiyishop_user")) {
            for (int i = 0; i < count; i++) {
                String username = "loadtest_" + i;
                long userId = findOrCreateUser(c, username);
                long addressId = findOrCreateAddress(c, userId, i);
                result.add(new long[]{userId, addressId});
            }
        }
        return result;
    }

    static long findOrCreateUser(Connection c, String username) throws Exception {
        try (PreparedStatement ps = c.prepareStatement("SELECT id FROM user WHERE username = ?")) {
            ps.setString(1, username);
            try (ResultSet r = ps.executeQuery()) {
                if (r.next()) {
                    return r.getLong(1);
                }
            }
        }
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO user (username, password_hash, nickname, status, deleted) VALUES (?, ?, ?, 1, 0)",
                Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, username);
            ps.setString(2, BCRYPT);
            ps.setString(3, "压测用户");
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                keys.next();
                return keys.getLong(1);
            }
        }
    }

    static long findOrCreateAddress(Connection c, long userId, int index) throws Exception {
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT id FROM user_address WHERE user_id = ? AND deleted = 0 LIMIT 1")) {
            ps.setLong(1, userId);
            try (ResultSet r = ps.executeQuery()) {
                if (r.next()) {
                    return r.getLong(1);
                }
            }
        }
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO user_address (user_id, receiver_name, receiver_phone, province, city, district, detail, is_default, deleted)"
                        + " VALUES (?, '压测收货人', ?, '浙江省', '杭州市', '余杭区', '压测路 1 号', 1, 0)",
                Statement.RETURN_GENERATED_KEYS)) {
            ps.setLong(1, userId);
            ps.setString(2, "138" + String.format("%08d", index % 100000000));
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                keys.next();
                return keys.getLong(1);
            }
        }
    }

    /**
     * 走真实登录接口拿 1000 个用户令牌（并发执行，顺带压一遍登录链路）。
     * <p>为什么不用自签令牌：服务端校验的是密钥 + 受众 + 令牌类型，自签容易在细节上失配，
     * 而登录接口本来就该被压测覆盖。
     */
    static int loginAndWriteTokens(List<long[]> users) throws Exception {
        Path file = Path.of(OUT_DIR, "tokens.csv");
        Files.deleteIfExists(file);
        Files.writeString(file, "token\n", StandardCharsets.UTF_8, StandardOpenOption.CREATE);

        List<String> tokens = java.util.Collections.synchronizedList(new ArrayList<>());
        int failures = 0;
        // 登录并发刻意收敛到 16：1000 个并发登录会先把（服务端）连接池与（客户端）套接字打满，
        // 那样测出来的是环境上限，不是登录接口的能力
        Semaphore permits = new Semaphore(16);
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<java.util.concurrent.Callable<String>> tasks = new ArrayList<>();
            for (int i = 0; i < users.size(); i++) {
                String username = "loadtest_" + i;
                tasks.add(() -> {
                    permits.acquire();
                    try {
                        String body = "{\"username\":\"" + username + "\",\"password\":\"" + PASSWORD + "\"}";
                        for (int attempt = 0; attempt < 3; attempt++) {
                            String response = post(GATEWAY + "/api/v1/auth/login", body, null);
                            Matcher matcher = Pattern.compile("\"accessToken\"\\s*:\\s*\"([^\"]+)\"").matcher(response);
                            if (matcher.find()) {
                                tokens.add(matcher.group(1));
                                return null;
                            }
                            Thread.sleep(200);
                        }
                        return null;
                    } finally {
                        permits.release();
                    }
                });
            }
            for (var future : executor.invokeAll(tasks)) {
                try {
                    future.get();
                } catch (Exception ex) {
                    failures++;
                }
            }
        }
        try (var writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8, StandardOpenOption.APPEND)) {
            for (String token : tokens) {
                writer.write(token);
                writer.newLine();
            }
        }
        if (tokens.size() < users.size()) {
            System.out.println("登录失败 " + (users.size() - tokens.size()) + " 个（已重试 3 次），成功 "
                    + tokens.size() + " 个" + (failures > 0 ? "，异常 " + failures + " 个" : ""));
        }
        return tokens.size();
    }

    static List<Long> onSaleProductIds(int limit) throws Exception {
        List<Long> ids = new ArrayList<>();
        try (Connection c = connect("baiyishop_product"); Statement s = c.createStatement();
             ResultSet r = s.executeQuery("SELECT id FROM product WHERE status = 'ON_SALE' AND deleted = 0"
                     + " ORDER BY sales DESC LIMIT " + limit)) {
            while (r.next()) {
                ids.add(r.getLong(1));
            }
        }
        return ids;
    }

    static void writeProducts(List<Long> products) throws Exception {
        Path file = Path.of(OUT_DIR, "products.csv");
        Files.deleteIfExists(file);
        try (var writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8, StandardOpenOption.CREATE)) {
            writer.write("pid");
            writer.newLine();
            for (Long id : products) {
                writer.write(String.valueOf(id));
                writer.newLine();
            }
        }
    }

    /** 建活动：普通库存 2000，划拨 1000 到秒杀池，每人限购 1（活动 2 小时，够压测） */
    static long createSeckillActivity() throws Exception {
        long productId;
        long skuId;
        try (Connection product = connect("baiyishop_product")) {
            productId = insertProduct(product, "压测秒杀商品");
            skuId = insertSku(product, productId);
        }
        try (Connection inventory = connect("baiyishop_inventory")) {
            insertInventory(inventory, skuId, productId, ALLOC_STOCK * 2);
        }
        String adminToken = adminToken();
        String body = "{\"name\":\"压测秒杀活动\",\"startTime\":\""
                + LocalDateTime.now().minusMinutes(5).format(TIME) + "\",\"endTime\":\""
                + LocalDateTime.now().plusHours(2).format(TIME) + "\",\"skus\":[{\"skuId\":" + skuId
                + ",\"seckillPrice\":100,\"allocStock\":" + ALLOC_STOCK + ",\"limitPerUser\":1}]}";
        String response = post(GATEWAY + "/api/v1/admin/seckill/activities", body, adminToken);
        Matcher matcher = Pattern.compile("\"skus\"\\s*:\\s*\\[\\s*\\{\\s*\"id\"\\s*:\\s*(\\d+)").matcher(response);
        if (!matcher.find()) {
            throw new IllegalStateException("创建秒杀活动失败：" + response);
        }
        return Long.parseLong(matcher.group(1));
    }

    static String adminToken() throws Exception {
        try (Connection c = connect("baiyishop_user"); Statement s = c.createStatement()) {
            s.executeUpdate("INSERT IGNORE INTO admin (username, password_hash, real_name, status, deleted)"
                    + " VALUES ('smokeadmin', '" + BCRYPT + "', '冒烟管理员', 1, 0)");
            s.executeUpdate("INSERT IGNORE INTO admin_role (admin_id, role_id)"
                    + " SELECT a.id, r.id FROM admin a JOIN `role` r ON r.code = 'SUPER_ADMIN'"
                    + " WHERE a.username = 'smokeadmin'");
        }
        String response = post(GATEWAY + "/api/v1/admin/auth/login",
                "{\"username\":\"smokeadmin\",\"password\":\"" + PASSWORD + "\"}", null);
        Matcher matcher = Pattern.compile("\"accessToken\"\\s*:\\s*\"([^\"]+)\"").matcher(response);
        if (!matcher.find()) {
            throw new IllegalStateException("后台登录失败：" + response);
        }
        return matcher.group(1);
    }

    static void writeProperties(long activitySkuId, int productCount) throws Exception {
        String content = "activitySkuId=" + activitySkuId + "\nproductCount=" + productCount + "\n"
                + "tokensCsv=" + OUT_DIR.replace("\\", "/") + "/tokens.csv\n"
                + "productsCsv=" + OUT_DIR.replace("\\", "/") + "/products.csv\n";
        Files.writeString(Path.of(OUT_DIR, "loadtest.properties"), content, StandardCharsets.UTF_8);
    }

    // ======================= JDBC / HTTP =======================

    static Connection connect(String schema) throws Exception {
        return DriverManager.getConnection(String.format(MYSQL, schema), "root", "root");
    }

    static long insertProduct(Connection c, String name) throws Exception {
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO product (name, category_id, main_image, status, min_price, sales, on_sale_time, deleted)"
                        + " VALUES (?, 1, 'https://minio/load.jpg', 'ON_SALE', 9900, 0, NOW(3), 0)",
                Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, name + System.currentTimeMillis());
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

    static String post(String url, String body, String token) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(30))
                .header("Content-Type", "application/json");
        if (token != null && !token.isBlank()) {
            // 注意：token 为空时不要送 "Bearer null"，否则会被网关判成非法令牌（10002）
            builder.header("Authorization", "Bearer " + token);
        }
        HttpResponse<String> response = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build()
                .send(builder.POST(HttpRequest.BodyPublishers.ofString(body)).build(),
                        HttpResponse.BodyHandlers.ofString());
        return response.body();
    }
}
