import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 百益商城 · 演示数据重置脚本（单文件 Java，直接用 JDBC + HTTP，无额外依赖）。
 *
 * <p>做两件事：
 * <ol>
 *   <li><b>清理</b>：删掉冒烟 / 压测留下的业务数据（分类、商品、库存、订单、支付、秒杀活动、用户…），
 *       只保留 RBAC 字典（role / permission / role_permission）与超级管理员登录能力。</li>
 *   <li><b>播种</b>：写入一份"能看的"演示数据 —— 三级分类树、品牌、参数模板、27 个商品与单规格 SKU、
 *       库存（含 3 条低库存预警）、首页配置（轮播 / 公告 / 金刚区 / 楼层）。</li>
 * </ol>
 * 随后通过<b>真实接口</b>补上会更"真"的数据：重建 ES 索引、注册演示买家并下 3 笔不同状态的订单
 * （待付款 / 待发货 / 待收货）、建 2 个秒杀场次（走后台接口，库存划拨与池子流水都由服务端生成）。
 *
 * <p>用法（推荐用 scripts/reset-demo-data.ps1 包装脚本）：
 * <pre>
 * java -cp mysql-connector-j-*.jar scripts/demo/DemoData.java
 * java -cp ... DemoData.java -SkipReset        # 只播种，不清库
 * java -cp ... DemoData.java -SkipHttp         # 只做 SQL 部分（服务没起时）
 * </pre>
 *
 * <p>账号（脚本跑完会打印）：
 * <ul>
 *   <li>后台：demo_admin / Admin@2026（超管）、demo_operator / Admin@2026（运营）、demo_service / Admin@2026（客服）</li>
 *   <li>前台：buyer01 / Demo@2026</li>
 * </ul>
 *
 * <p>图片说明：后端字段存图片 URL，本地没有可达的 MinIO 时前端会把"裸文件名"映射到
 * prototype/assets/img 下的占位图，所以这里填 headphone-01 这类占位图名，接真实 MinIO 后替换成 URL 即可。
 */
public class DemoData {

    // ---------------- 环境 ----------------
    static final String DB_HOST = env("DEMO_DB_HOST", "127.0.0.1");
    static final String DB_PORT = env("DEMO_DB_PORT", "3306");
    static final String DB_USER = env("DEMO_DB_USER", "root");
    static final String DB_PASS = env("DEMO_DB_PASS", "root");
    static final String GATEWAY = env("DEMO_GATEWAY", "http://localhost:8080");
    static final String SEARCH = env("DEMO_SEARCH", "http://localhost:8083");

    /** user 服务 V1 迁移里的 BCrypt 哈希，明文就是 Admin@2026（冒烟脚本同款，避免额外引入 BCrypt 依赖） */
    static final String ADMIN_HASH = "$2a$10$HbGzE7J275p/g4cBFABjrOiEioWA/2heMlKYA.Y6npBt/8rou36gW";
    static final String ADMIN_PASSWORD = "Admin@2026";
    static final String BUYER_USERNAME = "buyer01";
    static final String BUYER_PASSWORD = "Demo@2026";

    static final DateTimeFormatter ISO = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");
    static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    // ---------------- 种子数据：分类（三级） ----------------
    static final String[][] LEVEL1 = {
            {"数码电器", "smartphone"},
            {"服饰鞋包", "star"},
            {"家居生活", "house"},
            {"食品饮料", "package"},
            {"美妆个护", "shield-check"},
            {"母婴玩具", "store"},
            {"运动户外", "zap"},
            {"图书文娱", "list-filter"},
    };

    /** {一级, 二级} */
    static final String[][] LEVEL2 = {
            {"数码电器", "影音娱乐"}, {"数码电器", "电脑外设"}, {"数码电器", "智能生活"},
            {"服饰鞋包", "男装"}, {"服饰鞋包", "女装"}, {"服饰鞋包", "鞋靴"}, {"服饰鞋包", "箱包"}, {"服饰鞋包", "配饰"},
            {"家居生活", "家具"}, {"家居生活", "厨房用品"}, {"家居生活", "文具"},
            {"食品饮料", "咖啡冲调"}, {"食品饮料", "休闲零食"},
            {"美妆个护", "护肤"}, {"美妆个护", "洗护"},
            {"母婴玩具", "婴童用品"}, {"母婴玩具", "玩具"},
            {"运动户外", "运动装备"}, {"运动户外", "户外用品"},
            {"图书文娱", "图书"}, {"图书文娱", "文创周边"},
    };

    /** {一级, 二级, 三级} */
    static final String[][] LEVEL3 = {
            {"数码电器", "影音娱乐", "耳机音箱"}, {"数码电器", "影音娱乐", "相机摄影"},
            {"数码电器", "电脑外设", "键盘鼠标"}, {"数码电器", "电脑外设", "显示器"},
            {"数码电器", "智能生活", "智能穿戴"}, {"数码电器", "智能生活", "生活电器"},
            {"服饰鞋包", "男装", "T恤"}, {"服饰鞋包", "男装", "外套"},
            {"服饰鞋包", "女装", "牛仔裤"}, {"服饰鞋包", "女装", "连衣裙"},
            {"服饰鞋包", "鞋靴", "运动鞋"}, {"服饰鞋包", "鞋靴", "女鞋"},
            {"服饰鞋包", "箱包", "双肩包"}, {"服饰鞋包", "箱包", "行李箱"},
            {"服饰鞋包", "配饰", "眼镜"},
            {"家居生活", "家具", "座椅"}, {"家居生活", "厨房用品", "杯壶"}, {"家居生活", "文具", "笔记本"},
            {"食品饮料", "咖啡冲调", "咖啡豆"}, {"食品饮料", "休闲零食", "坚果炒货"},
            {"美妆个护", "护肤", "面部护理"}, {"美妆个护", "洗护", "洗发护发"},
            {"母婴玩具", "婴童用品", "喂养用品"}, {"母婴玩具", "玩具", "积木拼装"},
            {"运动户外", "运动装备", "健身器材"}, {"运动户外", "户外用品", "户外露营"},
            {"图书文娱", "图书", "经管励志"}, {"图书文娱", "文创周边", "手账周边"},
    };

    static final String[] BRANDS = {"百益严选", "云简", "木石", "北辰", "逐风"};

    // ---------------- 种子数据：参数模板 ----------------
    /** 模板名 -> 参数项（名, 单位） */
    static final Map<String, String[][]> PARAM_TEMPLATES = new LinkedHashMap<>();
    static {
        PARAM_TEMPLATES.put("通用参数", new String[][]{
                {"适用场景", null}, {"产地", null}, {"保修期", "月"}});
        PARAM_TEMPLATES.put("数码参数", new String[][]{
                {"连接方式", null}, {"电池容量", "mAh"}, {"屏幕尺寸", "英寸"}});
        PARAM_TEMPLATES.put("服饰参数", new String[][]{
                {"面料成分", null}, {"可选尺码", null}, {"洗涤方式", null}});
        PARAM_TEMPLATES.put("家居参数", new String[][]{
                {"主要材质", null}, {"规格", null}, {"适用面积", "㎡"}});
    }

    /** 商品种子：名称, 叶子分类, 品牌, 占位图, 价格(分), 销量, 初始库存, 上架于几天前 */
    static final Object[][] PRODUCTS = {
            {"头戴式主动降噪耳机 蓝牙 5.3", "耳机音箱", "云简", "headphone-01", 59900L, 1286, 120, 32},
            {"真无线蓝牙耳机 半入耳长续航", "耳机音箱", "北辰", "headphone-01", 19900L, 3264, 300, 18},
            {"便携微单相机 4K 视频 Vlog 套机", "相机摄影", "北辰", "camera-01", 429900L, 86, 6, 45},
            {"机械键盘 87 键 茶轴 有线", "键盘鼠标", "云简", "keyboard-01", 32900L, 942, 150, 26},
            {"双模无线鼠标 静音办公", "键盘鼠标", "云简", "mouse-01", 8900L, 2143, 400, 40},
            {"智能运动手表 GPS 心率血氧", "智能穿戴", "逐风", "watch-01", 89900L, 648, 90, 12},
            {"护眼台灯 无极调光 国 AA 级", "生活电器", "木石", "lamp-01", 15900L, 1782, 200, 55},
            {"精梳纯棉圆领 T 恤 不易变形", "T恤", "百益严选", "tee-01", 7900L, 5428, 600, 8},
            {"春秋薄款防风夹克 机能风", "外套", "百益严选", "jacket-01", 25900L, 836, 180, 15},
            {"直筒水洗牛仔裤 男款", "牛仔裤", "百益严选", "jeans-01", 18900L, 1624, 260, 22},
            {"轻量缓震运动鞋 透气网面", "运动鞋", "逐风", "sneaker-01", 23900L, 2318, 320, 5},
            {"通勤尖头细跟单鞋 5cm", "女鞋", "云简", "heel-01", 21900L, 472, 110, 38},
            {"轻便双肩包 15.6 寸电脑仓", "双肩包", "木石", "backpack-01", 12900L, 1356, 240, 30},
            {"偏光太阳镜 防紫外线 UV400", "眼镜", "北辰", "sunglass-01", 9900L, 864, 200, 48},
            {"人体工学电脑椅 网布透气", "座椅", "木石", "chair-01", 89900L, 326, 8, 60},
            {"陶瓷马克杯 350ml 情侣款", "杯壶", "木石", "mug-01", 3900L, 2764, 500, 20},
            {"硬壳线装笔记本 A5 横线", "笔记本", "百益严选", "notebook-01", 2900L, 3142, 700, 10},
            {"云南小粒咖啡豆 中深烘 454g", "咖啡豆", "云简", "coffee-01", 6900L, 1426, 180, 25},
            {"每日坚果混合装 30 袋", "坚果炒货", "百益严选", "coffee-01", 5900L, 3982, 400, 14},
            {"温和保湿爽肤水 200ml", "面部护理", "云简", "bottle-01", 8900L, 1524, 260, 35},
            {"氨基酸洗发水 无硅油 500ml", "洗发护发", "云简", "bottle-01", 6900L, 1863, 320, 28},
            {"婴儿宽口径玻璃奶瓶 240ml", "喂养用品", "百益严选", "bottle-01", 9900L, 528, 160, 42},
            {"儿童益智大颗粒积木 200 粒", "积木拼装", "逐风", "notebook-01", 11900L, 786, 220, 16},
            {"TPE 加厚防滑瑜伽垫 6mm", "健身器材", "逐风", "chair-01", 8900L, 1684, 9, 6},
            {"户外露营折叠椅 承重 120kg", "户外露营", "逐风", "chair-01", 13900L, 964, 240, 9},
            {"复古手账套装 含贴纸胶带", "手账周边", "木石", "notebook-01", 5900L, 642, 300, 19},
            {"《电商运营实战》从 0 到 1", "经管励志", "百益严选", "notebook-01", 6800L, 412, 200, 50},
            {"316 不锈钢保温杯 500ml", "杯壶", "木石", "bottle-01", 9900L, 1864, 380, 11},
            {"活页笔记本 A5 可拆卸", "笔记本", "百益严选", "notebook-01", 3900L, 1242, 450, 7},
            {"实木餐椅 北欧原木风", "座椅", "木石", "chair-01", 45900L, 214, 120, 44},
            {"懒人沙发豆袋 可拆洗", "座椅", "木石", "chair-01", 29900L, 486, 140, 23},
            {"挂耳咖啡 10 片装 中烘", "咖啡豆", "云简", "coffee-01", 4900L, 2684, 520, 13},
            {"冷萃咖啡液 10 条装", "咖啡豆", "云简", "coffee-01", 3900L, 1526, 460, 4},
            {"原味巴旦木 500g", "坚果炒货", "百益严选", "coffee-01", 6900L, 926, 300, 36},
            {"玻尿酸补水面膜 10 片", "面部护理", "云简", "bottle-01", 6900L, 3246, 420, 2},
            {"氨基酸沐浴露 500ml", "洗发护发", "云简", "bottle-01", 5900L, 1128, 350, 17},
            {"婴儿手口湿巾 80 抽 × 6 包", "喂养用品", "百益严选", "bottle-01", 3900L, 2146, 500, 21},
            {"木质拼图玩具 100 片", "积木拼装", "逐风", "notebook-01", 5900L, 864, 260, 33},
            {"户外露营帐篷 3-4 人", "户外露营", "逐风", "backpack-01", 39900L, 324, 90, 27},
            {"可调节哑铃 20kg 一对", "健身器材", "逐风", "chair-01", 25900L, 268, 110, 41},
            {"《数据结构与算法图解》", "经管励志", "百益严选", "notebook-01", 8900L, 386, 240, 29},
            {"中性笔套装 0.5mm 10 支", "手账周边", "木石", "notebook-01", 2900L, 1624, 600, 12},
    };

    // ====================================================================
    // 主流程
    // ====================================================================
    public static void main(String[] args) throws Exception {
        boolean skipReset = has(args, "-SkipReset");
        boolean skipHttp = has(args, "-SkipHttp");

        System.out.println("========================================================");
        System.out.println(" 百益商城 · 演示数据重置");
        System.out.println(" 库：" + DB_HOST + ":" + DB_PORT + "   网关：" + GATEWAY);
        System.out.println("========================================================");

        // 先确保演示管理员可用、且密码能登录，再去删数据：万一哈希失效也不会把后台锁死
        createAdmins();
        if (!skipHttp) {
            adminLogin();
        }

        if (!skipReset) {
            System.out.println("\n[1/6] 清理冒烟 / 压测数据…");
            resetAll();
        } else {
            System.out.println("\n[1/6] 跳过清理（-SkipReset）");
            long existing;
            try (Connection c = db("baiyishop_product")) {
                existing = count(c, "product");
            }
            if (existing > 0) {
                throw new IllegalStateException("baiyishop_product.product 已有 " + existing
                        + " 条数据：-SkipReset 只适合在空库上播种（品牌/模板名有唯一约束，重复播种会报错）。"
                        + "要清库重来请去掉该参数。");
            }
        }

        System.out.println("\n[2/6] 写入分类 / 品牌 / 参数模板 / 商品 / 库存 / 首页配置…");
        Seed seed = seedBusinessData();
        System.out.println("      分类 " + seed.categoryCount + " 个（一级 " + LEVEL1.length + " / 二级 "
                + LEVEL2.length + " / 三级 " + LEVEL3.length + "）");
        System.out.println("      品牌 " + BRANDS.length + " 个，参数模板 " + PARAM_TEMPLATES.size()
                + " 个，商品 " + PRODUCTS.length + " 个（各 1 个默认 SKU）");

        System.out.println("\n[3/6] 确保演示买家就绪…");
        String buyerToken = null;
        if (!skipHttp) {
            buyerToken = ensureBuyer();
        }

        if (!skipHttp) {
            System.out.println("\n[4/6] 通过接口重建 ES 索引…");
            String reindex = ok("全量重建索引", post(SEARCH + "/internal/search/reindex", "{}", null, null));
            System.out.println("      索引 " + str(reindex, "index") + "，文档 " + num(reindex, "indexed") + " 条");

            System.out.println("\n[5/6] 通过接口造 3 笔不同状态的演示订单 + 2 个秒杀场次…");
            createDemoOrders(buyerToken);
            createDemoSeckill();
        } else {
            System.out.println("\n[4/6][5/6] 跳过接口部分（-SkipHttp）：索引未重建、无演示订单与秒杀场次");
        }

        System.out.println("\n[6/6] 校验…");
        verify(seed, !skipHttp);
        printSummary(skipHttp);
    }

    // ====================================================================
    // 清理
    // ====================================================================
    static void resetAll() throws SQLException {
        Map<String, String[]> plan = new LinkedHashMap<>();
        plan.put("baiyishop_order", new String[]{
                "DELETE FROM order_item", "DELETE FROM order_status_log", "DELETE FROM order_note",
                "DELETE FROM order_request", "DELETE FROM `order`", "DELETE FROM cart_item",
                "DELETE FROM mq_outbox", "DELETE FROM mq_consume_log"});
        plan.put("baiyishop_payment", new String[]{
                "DELETE FROM payment_callback_log", "DELETE FROM payment", "DELETE FROM mq_outbox"});
        plan.put("baiyishop_seckill", new String[]{
                "DELETE FROM seckill_record", "DELETE FROM seckill_activity_sku", "DELETE FROM seckill_activity",
                "DELETE FROM mq_outbox", "DELETE FROM mq_consume_log"});
        plan.put("baiyishop_inventory", new String[]{
                "DELETE FROM seckill_stock_flow", "DELETE FROM seckill_stock_pool",
                "DELETE FROM inventory_flow", "DELETE FROM stock_alert", "DELETE FROM inventory",
                "DELETE FROM mq_outbox", "DELETE FROM mq_consume_log"});
        plan.put("baiyishop_product", new String[]{
                "DELETE FROM product_param_value", "DELETE FROM product_image", "DELETE FROM product_sku",
                "DELETE FROM product", "DELETE FROM category", "DELETE FROM brand",
                "DELETE FROM param_item", "DELETE FROM param_template",
                "DELETE FROM home_floor_item", "DELETE FROM home_floor",
                "DELETE FROM home_banner", "DELETE FROM home_notice", "DELETE FROM home_nav",
                "DELETE FROM mq_outbox"});
        plan.put("baiyishop_user", new String[]{
                "DELETE FROM user_address", "DELETE FROM user_wechat", "DELETE FROM user",
                "DELETE FROM admin_role WHERE admin_id NOT IN (SELECT id FROM (SELECT id FROM admin WHERE username IN ('"
                        + DEMO_ADMINS.get(0)[0] + "','" + DEMO_ADMINS.get(1)[0] + "','" + DEMO_ADMINS.get(2)[0] + "')) t)",
                "DELETE FROM admin WHERE username NOT IN ('"
                        + DEMO_ADMINS.get(0)[0] + "','" + DEMO_ADMINS.get(1)[0] + "','" + DEMO_ADMINS.get(2)[0] + "')"});

        for (Map.Entry<String, String[]> e : plan.entrySet()) {
            long before = 0;
            try (Connection c = db(e.getKey())) {
                for (String sql : e.getValue()) {
                    try (Statement st = c.createStatement()) {
                        before += st.executeUpdate(sql);
                    }
                }
            }
            System.out.println("      " + e.getKey() + " 清理 " + before + " 行");
        }
        System.out.println("      保留：role / permission / role_permission（RBAC 字典）");
    }

    // ====================================================================
    // 管理员 / 用户
    // ====================================================================
    /** 演示管理员：{用户名, 姓名, 角色码} */
    static final List<String[]> DEMO_ADMINS = List.of(
            new String[]{"demo_admin", "百益超管", "SUPER_ADMIN"},
            new String[]{"demo_operator", "百益运营", "OPERATOR"},
            new String[]{"demo_service", "百益客服", "SERVICE"});

    static void createAdmins() throws SQLException {
        try (Connection c = db("baiyishop_user")) {
            for (String[] admin : DEMO_ADMINS) {
                insert(c, "INSERT INTO admin (username, password_hash, real_name, status, deleted) VALUES (?,?,?,1,0)"
                                + " ON DUPLICATE KEY UPDATE password_hash = VALUES(password_hash), real_name = VALUES(real_name), status = 1, deleted = 0",
                        admin[0], ADMIN_HASH, admin[1]);
                try (PreparedStatement ps = c.prepareStatement(
                        "INSERT IGNORE INTO admin_role (admin_id, role_id)"
                                + " SELECT a.id, r.id FROM admin a JOIN `role` r ON r.code = ? WHERE a.username = ?")) {
                    ps.setString(1, admin[2]);
                    ps.setString(2, admin[0]);
                    ps.executeUpdate();
                }
            }
        }
    }

    /** 注册（或复用）演示买家，并确保有一个默认收货地址 */
    static String ensureBuyer() throws Exception {
        String loginBody = "{\"username\":\"" + BUYER_USERNAME + "\",\"password\":\"" + BUYER_PASSWORD + "\"}";
        String login = post(GATEWAY + "/api/v1/auth/login", loginBody, null, null);
        if (code(login) != 0) {
            String register = post(GATEWAY + "/api/v1/auth/register",
                    "{\"username\":\"" + BUYER_USERNAME + "\",\"password\":\"" + BUYER_PASSWORD + "\",\"nickname\":\"百益小买家\"}",
                    null, null);
            if (code(register) != 0 && str(register, "message") != null
                    && !str(register, "message").contains("已注册")) {
                throw new IllegalStateException("注册演示买家失败：" + register);
            }
            login = ok("演示买家登录", post(GATEWAY + "/api/v1/auth/login", loginBody, null, null));
        }
        String token = str(login, "accessToken");
        String addresses = ok("地址列表", get(GATEWAY + "/api/v1/addresses", token));
        if (countOfJsonArray(addresses, "data") > 0) {
            System.out.println("      演示买家 " + BUYER_USERNAME + " 已存在且有收货地址");
        } else {
            ok("新增收货地址", post(GATEWAY + "/api/v1/addresses",
                    "{\"receiverName\":\"李百益\",\"receiverPhone\":\"13800138000\",\"province\":\"浙江省\","
                            + "\"city\":\"杭州市\",\"district\":\"西湖区\",\"detail\":\"文三路 100 号百益大厦 8 楼\",\"isDefault\":true}",
                    token, null));
            System.out.println("      演示买家 " + BUYER_USERNAME + " 已就绪（含默认地址）");
        }
        return token;
    }

    // ====================================================================
    // 业务种子数据
    // ====================================================================
    static class Seed {
        int categoryCount;
        final Map<String, Long> categoryIdByName = new LinkedHashMap<>();
        final Map<String, Long> brandIdByName = new LinkedHashMap<>();
        final Map<String, Long> productIdByName = new LinkedHashMap<>();
        final Map<String, Long> skuIdByName = new LinkedHashMap<>();
    }

    static Seed seedBusinessData() throws SQLException {
        Seed seed = new Seed();
        try (Connection c = db("baiyishop_product")) {
            seedCategories(c, seed);
            seedBrands(c, seed);
            Map<String, Long> paramItems = seedParamTemplates(c);
            seedProducts(c, seed, paramItems);
            seedHomeConfig(c, seed);
        }
        return seed;
    }

    static void seedCategories(Connection c, Seed seed) throws SQLException {
        int sort = 0;
        for (String[] l1 : LEVEL1) {
            long id = insert(c, "INSERT INTO category (parent_id, name, level, path, icon, sort, visible, deleted)"
                            + " VALUES (0,?,1,?,?,?,1,0)", l1[0], "/" + l1[0] + "/", l1[1], sort += 10);
            // path 里必须是自己真正的 id，插入后回填（与 CategoryService.pathOf 一致：父 path + id + /）
            update(c, "UPDATE category SET path = ? WHERE id = ?", "/" + id + "/", id);
            seed.categoryIdByName.put(l1[0], id);
        }
        int sort2 = 0;
        for (String[] l2 : LEVEL2) {
            Long parent = seed.categoryIdByName.get(l2[0]);
            long id = insert(c, "INSERT INTO category (parent_id, name, level, path, icon, sort, visible, deleted)"
                    + " VALUES (?,?,2,'',NULL,?,1,0)", parent, l2[1], sort2 += 10);
            update(c, "UPDATE category SET path = ? WHERE id = ?", "/" + parent + "/" + id + "/", id);
            seed.categoryIdByName.put(l2[1], id);
        }
        int sort3 = 0;
        for (String[] l3 : LEVEL3) {
            Long parent = seed.categoryIdByName.get(l3[1]);
            long id = insert(c, "INSERT INTO category (parent_id, name, level, path, icon, sort, visible, deleted)"
                    + " VALUES (?,?,3,'',NULL,?,1,0)", parent, l3[2], sort3 += 10);
            update(c, "UPDATE category SET path = ? WHERE id = ?", "/" + seed.categoryIdByName.get(l3[0]) + "/" + parent + "/" + id + "/", id);
            seed.categoryIdByName.put(l3[2], id);
        }
        seed.categoryCount = LEVEL1.length + LEVEL2.length + LEVEL3.length;
    }

    static void seedBrands(Connection c, Seed seed) throws SQLException {
        int sort = 0;
        for (String name : BRANDS) {
            long id = insert(c, "INSERT INTO brand (name, logo, description, sort, enabled, deleted) VALUES (?,?,?,?,1,0)",
                    name, null, name + " · 百益商城自营品牌", sort += 10);
            seed.brandIdByName.put(name, id);
        }
    }

    static Map<String, Long> seedParamTemplates(Connection c) throws SQLException {
        Map<String, Long> items = new LinkedHashMap<>();   // "模板/参数项" -> param_item.id
        int tplSort = 0;
        for (Map.Entry<String, String[][]> tpl : PARAM_TEMPLATES.entrySet()) {
            long tplId = insert(c, "INSERT INTO param_template (name, sort, enabled, deleted) VALUES (?,?,1,0)",
                    tpl.getKey(), tplSort += 10);
            int itemSort = 0;
            for (String[] item : tpl.getValue()) {
                long itemId = insert(c, "INSERT INTO param_item (template_id, name, unit, sort, deleted) VALUES (?,?,?,?,0)",
                        tplId, item[0], item[1], itemSort += 10);
                items.put(tpl.getKey() + "/" + item[0], itemId);
            }
        }
        return items;
    }

    static void seedProducts(Connection c, Seed seed, Map<String, Long> paramItems) throws SQLException {
        int index = 0;
        for (Object[] row : PRODUCTS) {
            index++;
            String name = (String) row[0];
            String leaf = (String) row[1];
            String brand = (String) row[2];
            String image = (String) row[3];
            long price = (Long) row[4];
            int sales = (Integer) row[5];
            int stock = (Integer) row[6];
            int daysOld = (Integer) row[7];

            Long categoryId = seed.categoryIdByName.get(leaf);
            if (categoryId == null) {
                throw new IllegalStateException("叶子分类不存在：" + leaf);
            }
            String level1 = level1Of(leaf);

            long productId = insert(c, "INSERT INTO product (name, category_id, brand_id, main_image, detail, status,"
                            + " min_price, sales, on_sale_time, deleted)"
                            + " VALUES (?,?,?,?,?, 'ON_SALE', ?,?, DATE_SUB(NOW(3), INTERVAL ? DAY), 0)",
                    name, categoryId, seed.brandIdByName.get(brand), image,
                    "<p>" + name + "，百益商城自营，正品保障。</p><p>全场包邮，48 小时内发出；"
                            + "发货后 7 天未确认收货将自动确认。未支付订单 15 分钟自动取消并释放库存。</p>",
                    price, sales, daysOld);
            seed.productIdByName.put(name, productId);

            long skuId = insert(c, "INSERT INTO product_sku (product_id, sku_code, spec_name, price, image, sort, status, deleted)"
                            + " VALUES (?,?, '默认规格', ?, NULL, 0, 1, 0)",
                    productId, productId + "-01", price);
            seed.skuIdByName.put(name, skuId);

            // 图集：首图与主图一致，再补两张
            insert(c, "INSERT INTO product_image (product_id, url, sort) VALUES (?,?,0)", productId, image);
            insert(c, "INSERT INTO product_image (product_id, url, sort) VALUES (?,?,1)", productId, nextImage(image, 1));
            insert(c, "INSERT INTO product_image (product_id, url, sort) VALUES (?,?,2)", productId, nextImage(image, 2));

            seedParamValues(c, productId, level1, index, paramItems);

            seedInventory(skuId, productId, stock);
        }
    }

    /** 库存写在 baiyishop_inventory 库（与商品表不同库），所以单独开连接 */
    static void seedInventory(long skuId, long productId, int stock) throws SQLException {
        try (Connection c = db("baiyishop_inventory")) {
            insert(c, "INSERT INTO inventory (sku_id, product_id, available, locked, warn_threshold, version)"
                    + " VALUES (?,?,?,0,10,0)", skuId, productId, stock);
            if (stock < 10) {
                insert(c, "INSERT INTO stock_alert (sku_id, product_id, current_stock, threshold, status, updated_at)"
                        + " VALUES (?,?,?,10,'OPEN',NOW(3))", skuId, productId, stock);
            }
        }
    }

    /**
     * 每个商品写**一个**参数模板的值。
     * <p>后端校验一个商品的参数项必须来自同一个模板（PARAM_ITEM_TEMPLATE_MISMATCH），
     * 所以这里按一级分类挑最合适的那套：数码→数码参数、服饰→服饰参数、家居→家居参数、其余→通用参数。
     */
    static void seedParamValues(Connection c, long productId, String level1, int index, Map<String, Long> items) throws SQLException {
        List<String[]> values = new ArrayList<>();
        switch (level1) {
            case "数码电器" -> {
                values.add(new String[]{"数码参数/连接方式", index % 2 == 0 ? "蓝牙 5.3 / USB-C" : "蓝牙 5.2"});
                values.add(new String[]{"数码参数/电池容量", String.valueOf(300 + index * 50)});
                values.add(new String[]{"数码参数/屏幕尺寸", index % 2 == 0 ? "1.4" : "0.96"});
            }
            case "服饰鞋包" -> {
                values.add(new String[]{"服饰参数/面料成分", index % 2 == 0 ? "棉 95% / 氨纶 5%" : "聚酯纤维 100%"});
                values.add(new String[]{"服饰参数/可选尺码", "S / M / L / XL"});
                values.add(new String[]{"服饰参数/洗涤方式", "机洗 30℃ 以下，不可漂白"});
            }
            case "家居生活" -> {
                values.add(new String[]{"家居参数/主要材质", index % 2 == 0 ? "实木 + 金属" : "陶瓷"});
                values.add(new String[]{"家居参数/规格", "常规款"});
                values.add(new String[]{"家居参数/适用面积", String.valueOf(10 + index % 3 * 5)});
            }
            default -> {
                values.add(new String[]{"通用参数/适用场景", switch (level1) {
                    case "食品饮料" -> "日常饮用 / 办公";
                    case "美妆个护" -> "日常护理";
                    case "母婴玩具" -> "家庭日常";
                    case "运动户外" -> "运动健身 / 户外";
                    default -> "日常阅读 / 学习";
                }});
                values.add(new String[]{"通用参数/产地", "浙江杭州"});
                values.add(new String[]{"通用参数/保修期", index % 3 == 0 ? "24" : "12"});
            }
        }

        int sort = 0;
        for (String[] pair : values) {
            Long itemId = items.get(pair[0]);
            if (itemId == null) {
                continue;
            }
            insert(c, "INSERT INTO product_param_value (product_id, param_item_id, value, sort) VALUES (?,?,?,?)",
                    productId, itemId, pair[1], sort += 10);
        }
    }

    static void seedHomeConfig(Connection c, Seed seed) throws SQLException {
        long digital = seed.categoryIdByName.get("数码电器");
        long apparel = seed.categoryIdByName.get("服饰鞋包");
        long home = seed.categoryIdByName.get("家居生活");

        insert(c, "INSERT INTO home_banner (title, image_url, link_type, link_value, sort, enabled) VALUES (?,?,2,?,1,1)",
                "新品首发 · 数码好物", "banner-01", String.valueOf(digital));
        insert(c, "INSERT INTO home_banner (title, image_url, link_type, link_value, sort, enabled) VALUES (?,?,2,?,2,1)",
                "春夏穿搭 · 全场包邮", "banner-02", String.valueOf(apparel));
        insert(c, "INSERT INTO home_banner (title, image_url, link_type, link_value, sort, enabled) VALUES (?,?,2,?,3,1)",
                "家居焕新 · 品质生活", "banner-03", String.valueOf(home));

        insert(c, "INSERT INTO home_notice (content, sort, enabled) VALUES (?,1,1)",
                "全场包邮 · 48 小时内发货，发货后 7 天自动确认收货");
        insert(c, "INSERT INTO home_notice (content, sort, enabled) VALUES (?,2,1)",
                "午间秒杀进行中，每人限购由后台配置，售完即止");

        String[][] navs = {
                {"数码电器", "smartphone"}, {"服饰鞋包", "star"}, {"家居生活", "house"},
                {"食品饮料", "package"}, {"运动户外", "zap"}, {"图书文娱", "list-filter"}};
        int navSort = 0;
        for (String[] nav : navs) {
            insert(c, "INSERT INTO home_nav (name, icon, category_id, sort, enabled) VALUES (?,?,?,?,1)",
                    nav[0], nav[1], seed.categoryIdByName.get(nav[0]), navSort += 10);
        }

        // sort_field：1 销量 / 2 上新 / 3 价格升 / 4 价格降（HomeFloor 常量）
        insert(c, "INSERT INTO home_floor (title, category_id, sort_field, limit_size, sort, enabled) VALUES (?,?,1,8,1,1)",
                "数码电器 · 热销榜", digital);
        insert(c, "INSERT INTO home_floor (title, category_id, sort_field, limit_size, sort, enabled) VALUES (?,?,1,8,2,1)",
                "服饰鞋包 · 人气单品", apparel);
        insert(c, "INSERT INTO home_floor (title, category_id, sort_field, limit_size, sort, enabled) VALUES (?,?,3,8,3,1)",
                "家居生活 · 高性价比", home);
        insert(c, "INSERT INTO home_floor (title, category_id, sort_field, limit_size, sort, enabled) VALUES (?,?,2,8,4,1)",
                "美妆个护 · 本周上新", seed.categoryIdByName.get("美妆个护"));
    }

    // ====================================================================
    // 通过接口造订单与秒杀
    // ====================================================================
    static void createDemoOrders(String buyerToken) throws Exception {
        String addressId = firstAddressId(buyerToken);
        List<String[]> orders = new ArrayList<>();
        orders.add(new String[]{"头戴式主动降噪耳机 蓝牙 5.3", "1", "待付款"});
        orders.add(new String[]{"精梳纯棉圆领 T 恤 不易变形", "2", "待发货"});
        orders.add(new String[]{"陶瓷马克杯 350ml 情侣款", "3", "待收货"});

        List<String> shipped = new ArrayList<>();
        for (String[] order : orders) {
            long skuId = skuIdOf(order[0]);
            String orderNo = placeAndPay(buyerToken, skuId, Integer.parseInt(order[1]), addressId,
                    !"待付款".equals(order[2]));
            System.out.println("      演示订单 " + order[2] + "：" + orderNo + "（" + order[0] + " x" + order[1] + "）");
            if ("待收货".equals(order[2])) {
                shipped.add(orderNo);
            }
        }

        if (!shipped.isEmpty()) {
            String adminToken = adminLogin();
            for (String orderNo : shipped) {
                waitForStatus(buyerToken, orderNo, "PENDING_SHIPMENT", 30);
                ok("后台发货", post(GATEWAY + "/api/v1/admin/orders/" + orderNo + "/ship",
                        "{\"trackingNo\":\"SF" + String.format("%010d", Math.abs(orderNo.hashCode()) % 1000000000) + "\"}",
                        adminToken, null));
                System.out.println("      已发货：" + orderNo + "（运单号已记录，订单进入待收货）");
            }
        }
    }

    /** 加购 → 结算 → 下单 →（可选）支付，返回订单号 */
    static String placeAndPay(String token, long skuId, int quantity, String addressId, boolean pay) throws Exception {
        ok("加入购物车", post(GATEWAY + "/api/v1/carts/items",
                "{\"skuId\":" + skuId + ",\"quantity\":" + quantity + "}", token, null));
        String cart = ok("读取购物车", get(GATEWAY + "/api/v1/carts", token));
        List<String> itemIds = cartItemIds(cart);
        if (itemIds.isEmpty()) {
            throw new IllegalStateException("购物车为空，无法下单：添加 SKU " + skuId + " 后仍未取到条目");
        }
        String ids = String.join(",", itemIds);
        ok("结算试算", post(GATEWAY + "/api/v1/orders/settle",
                "{\"source\":\"CART\",\"cartItemIds\":[" + ids + "]}", token, null));
        String created = ok("提交订单", post(GATEWAY + "/api/v1/orders",
                "{\"source\":\"CART\",\"cartItemIds\":[" + ids + "],\"addressId\":" + addressId
                        + ",\"remark\":\"演示数据 · 联调用\"}", token, uuid()));
        String orderNo = str(created, "orderNo");
        if (!pay) {
            return orderNo;
        }
        String payment = ok("发起支付", post(GATEWAY + "/api/v1/payments",
                "{\"orderNo\":\"" + orderNo + "\",\"channel\":\"" + (((Math.abs(orderNo.hashCode()) % 2 == 0)) ? "WECHAT" : "ALIPAY") + "\"}",
                token, uuid()));
        ok("模拟支付", post(GATEWAY + "/api/v1/payments/" + str(payment, "paymentNo") + "/mock-pay", "{}", token, null));
        return orderNo;
    }

    /** 支付成功后订单状态由支付事件异步推进，这里轮询等到目标状态 */
    static void waitForStatus(String token, String orderNo, String expected, int seconds) throws Exception {
        long deadline = System.currentTimeMillis() + seconds * 1000L;
        while (System.currentTimeMillis() < deadline) {
            String detail = ok("订单详情", get(GATEWAY + "/api/v1/orders/" + orderNo, token));
            if (expected.equals(str(detail, "status"))) {
                return;
            }
            Thread.sleep(1000);
        }
        throw new IllegalStateException("等待订单 " + orderNo + " 进入 " + expected + " 超时（支付事件未推进？）");
    }

    static void createDemoSeckill() throws Exception {
        String adminToken = adminLogin();
        String running = "{\"name\":\"午间秒杀 · 限时 2 小时\",\"startTime\":\""
                + LocalDateTime.now().minusMinutes(30).format(ISO) + "\",\"endTime\":\""
                + LocalDateTime.now().plusMinutes(90).format(ISO) + "\",\"skus\":["
                + seckillSku("头戴式主动降噪耳机 蓝牙 5.3", 39900L, 20, 1, 0) + ","
                + seckillSku("轻量缓震运动鞋 透气网面", 14900L, 30, 2, 1) + "]}";
        ok("创建秒杀活动（进行中）", post(GATEWAY + "/api/v1/admin/seckill/activities", running, adminToken, null));

        String upcoming = "{\"name\":\"晚间秒杀 · 20 点场\",\"startTime\":\""
                + LocalDateTime.now().plusHours(3).format(ISO) + "\",\"endTime\":\""
                + LocalDateTime.now().plusHours(5).format(ISO) + "\",\"skus\":["
                + seckillSku("人体工学电脑椅 网布透气", 49900L, 5, 1, 0) + "]}";
        ok("创建秒杀活动（即将开始）", post(GATEWAY + "/api/v1/admin/seckill/activities", upcoming, adminToken, null));
        System.out.println("      已建 2 个秒杀场次：进行中 2 个 SKU / 即将开始 1 个 SKU（库存已从普通库存划拨）");
    }

    static String seckillSku(String productName, long seckillPrice, int allocStock, int limitPerUser, int sort)
            throws SQLException {
        return "{\"skuId\":" + skuIdOf(productName) + ",\"seckillPrice\":" + seckillPrice
                + ",\"allocStock\":" + allocStock + ",\"limitPerUser\":" + limitPerUser + ",\"sort\":" + sort + "}";
    }

    static String adminLogin() throws Exception {
        String body = "{\"username\":\"" + DEMO_ADMINS.get(0)[0] + "\",\"password\":\"" + ADMIN_PASSWORD + "\"}";
        String login = ok("后台登录", post(GATEWAY + "/api/v1/admin/auth/login", body, null, null));
        System.out.println("      后台登录成功：" + DEMO_ADMINS.get(0)[0] + "（" + DEMO_ADMINS.get(0)[2] + "）");
        return str(login, "accessToken");
    }

    // ====================================================================
    // 校验与输出
    // ====================================================================
    static void verify(Seed seed, boolean httpChecked) throws Exception {
        Map<String, String[]> counts = new LinkedHashMap<>();
        counts.put("baiyishop_product", new String[]{"category", "brand", "product", "product_sku",
                "product_param_value", "home_banner", "home_notice", "home_nav", "home_floor"});
        counts.put("baiyishop_inventory", new String[]{"inventory", "stock_alert"});
        counts.put("baiyishop_user", new String[]{"user", "user_address", "admin"});
        for (Map.Entry<String, String[]> e : counts.entrySet()) {
            StringBuilder line = new StringBuilder();
            try (Connection c = db(e.getKey())) {
                for (String table : e.getValue()) {
                    line.append(table).append('=').append(count(c, table)).append("  ");
                }
            }
            System.out.println("      " + e.getKey() + "：" + line);
        }
        if (!httpChecked) {
            return;
        }
        String home = ok("首页聚合", get(GATEWAY + "/api/v1/home", null));
        String search = ok("搜索", get(GATEWAY + "/api/v1/search/products?keyword="
                + java.net.URLEncoder.encode("耳机", StandardCharsets.UTF_8) + "&size=1", null));
        String seckill = ok("秒杀场次", get(GATEWAY + "/api/v1/seckill/activities?limit=10", null));
        System.out.println("      接口校验：首页楼层 " + countOfJsonArray(home, "floors") + " 个 / 轮播 "
                + countOfJsonArray(home, "banners") + " 个；搜索「耳机」命中 " + num(search, "total")
                + " 件；秒杀场次 " + countOfJsonArray(seckill, null) + " 个");
    }

    static void printSummary(boolean skipHttp) {
        System.out.println();
        System.out.println("========================================================");
        System.out.println(" 演示数据就绪");
        System.out.println("--------------------------------------------------------");
        System.out.println(" 后台   http://localhost:5173/admin/login.html");
        System.out.println("   demo_admin    / " + ADMIN_PASSWORD + "   超级管理员");
        System.out.println("   demo_operator / " + ADMIN_PASSWORD + "   运营");
        System.out.println("   demo_service  / " + ADMIN_PASSWORD + "   客服（仅查单 + 备注）");
        System.out.println(" 前台   http://localhost:5173/index.html");
        System.out.println("   " + BUYER_USERNAME + " / " + BUYER_PASSWORD + "   已含 3 笔不同状态的订单");
        System.out.println(" 小程序 http://localhost:5173/miniapp/index.html  （我的 → 微信授权登录）");
        if (skipHttp) {
            System.out.println(" 注意：本次带了 -SkipHttp，ES 索引未重建，搜索可能查不到新商品");
        }
        System.out.println("========================================================");
    }

    // ====================================================================
    // 小工具
    // ====================================================================
    static String env(String key, String def) {
        String value = System.getenv(key);
        return value == null || value.isBlank() ? def : value;
    }

    static boolean has(String[] args, String flag) {
        for (String arg : args) {
            if (flag.equalsIgnoreCase(arg)) {
                return true;
            }
        }
        return false;
    }

    static Connection db(String schema) throws SQLException {
        return DriverManager.getConnection("jdbc:mysql://" + DB_HOST + ":" + DB_PORT + "/" + schema
                + "?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&useSSL=false"
                + "&allowPublicKeyRetrieval=true&rewriteBatchedStatements=true", DB_USER, DB_PASS);
    }

    static long insert(Connection c, String sql, Object... args) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            bind(ps, args);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                return keys.next() ? keys.getLong(1) : 0L;
            }
        }
    }

    static void update(Connection c, String sql, Object... args) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            bind(ps, args);
            ps.executeUpdate();
        }
    }

    static void bind(PreparedStatement ps, Object[] args) throws SQLException {
        for (int i = 0; i < args.length; i++) {
            ps.setObject(i + 1, args[i]);
        }
    }

    static long count(Connection c, String table) throws SQLException {
        try (Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM `" + table + "`")) {
            rs.next();
            return rs.getLong(1);
        }
    }

    static String level1Of(String leafName) {
        for (String[] l3 : LEVEL3) {
            if (l3[2].equals(leafName)) {
                return l3[0];
            }
        }
        for (String[] l2 : LEVEL2) {
            if (l2[1].equals(leafName)) {
                return l2[0];
            }
        }
        throw new IllegalStateException("未知分类：" + leafName);
    }

    /** 图集里第 n 张备用图：从占位图集合里按名字取一张不同的 */
    static String nextImage(String image, int offset) {
        String[] pool = {"headphone-01", "keyboard-01", "backpack-01", "mug-01", "tee-01", "camera-01",
                "watch-01", "sneaker-01", "notebook-01", "chair-01", "bottle-01", "sunglass-01"};
        int base = 0;
        for (int i = 0; i < pool.length; i++) {
            if (pool[i].equals(image)) {
                base = i;
            }
        }
        return pool[(base + offset) % pool.length];
    }

    static long skuIdOf(String productName) throws SQLException {
        try (Connection c = db("baiyishop_product");
             PreparedStatement ps = c.prepareStatement(
                     "SELECT s.id FROM product_sku s JOIN product p ON p.id = s.product_id WHERE p.name = ?")) {
            ps.setString(1, productName);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new IllegalStateException("找不到商品的 SKU：" + productName);
                }
                return rs.getLong(1);
            }
        }
    }

    static String firstAddressId(String token) throws Exception {
        String list = ok("地址列表", get(GATEWAY + "/api/v1/addresses", token));
        Matcher m = Pattern.compile("\"id\"\\s*:\\s*(\\d+)").matcher(list);
        if (!m.find()) {
            throw new IllegalStateException("演示买家没有收货地址：" + list);
        }
        return m.group(1);
    }

    static List<String> cartItemIds(String cartJson) {
        List<String> ids = new ArrayList<>();
        Matcher m = Pattern.compile("\"id\"\\s*:\\s*(\\d+)").matcher(cartJson);
        while (m.find()) {
            ids.add(m.group(1));
        }
        return ids;
    }

    static String uuid() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    // ---------------- HTTP ----------------
    static String send(String method, String url, String body, String token, String requestId) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(30))
                .header("Content-Type", "application/json");
        if (token != null) {
            builder.header("Authorization", "Bearer " + token);
        }
        if (requestId != null) {
            builder.header("X-Request-Id", requestId);
        }
        builder.method(method, HttpRequest.BodyPublishers.ofString(body == null ? "{}" : body, StandardCharsets.UTF_8));
        return HTTP.send(builder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)).body();
    }

    static String post(String url, String body, String token, String requestId) throws Exception {
        return send("POST", url, body, token, requestId);
    }

    static String get(String url, String token) throws Exception {
        return send("GET", url, null, token, null);
    }

    /** 断言统一响应体 code=0，失败直接抛错终止脚本 */
    static String ok(String action, String json) {
        long code = code(json);
        if (code != 0) {
            throw new IllegalStateException(action + " 失败：code=" + code + " body=" + json);
        }
        return json;
    }

    static long code(String json) {
        return num(json, "code");
    }

    static String str(String json, String key) {
        if (json == null) {
            return null;
        }
        Matcher m = Pattern.compile("\"" + key + "\"\\s*:\\s*\"([^\"]*)\"").matcher(json);
        return m.find() ? m.group(1) : null;
    }

    static long num(String json, String key) {
        if (json == null) {
            return -1;
        }
        Matcher m = Pattern.compile("\"" + key + "\"\\s*:\\s*(-?\\d+)").matcher(json);
        return m.find() ? Long.parseLong(m.group(1)) : -1;
    }

    /** 粗略统计 JSON 顶层数组长度：key 为 null 时说明响应 data 本身就是数组 */
    static int countOfJsonArray(String json, String key) {
        String data = key == null ? section(json, "data") : section(json, key);
        if (data == null) {
            return 0;
        }
        int depth = 0;
        int count = 0;
        for (int i = 0; i < data.length(); i++) {
            char ch = data.charAt(i);
            if (ch == '{' || ch == '[') {
                if (depth == 1 && ch == '{') {
                    count++;
                }
                depth++;
            } else if (ch == '}' || ch == ']') {
                depth--;
                if (depth == 0) {
                    break;
                }
            }
        }
        return count;
    }

    /** 取 "key": 之后的那个数组或对象的原文（按括号配平截取） */
    static String section(String json, String key) {
        int at = json.indexOf("\"" + key + "\"");
        if (at < 0) {
            return null;
        }
        int start = -1;
        for (int i = at + key.length() + 2; i < json.length(); i++) {
            char ch = json.charAt(i);
            if (ch == '[' || ch == '{') {
                start = i;
                break;
            }
        }
        if (start < 0) {
            return null;
        }
        int depth = 0;
        for (int i = start; i < json.length(); i++) {
            char ch = json.charAt(i);
            if (ch == '[' || ch == '{') {
                depth++;
            } else if (ch == ']' || ch == '}') {
                depth--;
                if (depth == 0) {
                    return json.substring(start, i + 1);
                }
            }
        }
        return null;
    }
}
