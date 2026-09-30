-- ============================================================================
-- 百益商城 · product 服务初始表结构
-- 依据 docs/database.md 第 4 章（baiyishop_product，13 张表）与第 1 章通用规范
-- 覆盖 REQ-201 ~ REQ-206、REQ-401 ~ REQ-403
-- 注意：本脚本一经执行不得修改，后续变更一律新增 V2__xxx.sql
-- ============================================================================

-- ---------- category ----------
CREATE TABLE IF NOT EXISTS `category` (
    id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    parent_id   BIGINT UNSIGNED NOT NULL DEFAULT 0      COMMENT '父分类，0 为顶级',
    name        VARCHAR(50)     NOT NULL                COMMENT '分类名',
    level       TINYINT         NOT NULL DEFAULT 1      COMMENT '层级 1 / 2 / 3',
    path        VARCHAR(100)    NOT NULL                COMMENT '物化路径，如 /1/12/135/，含子分类查询用',
    icon        VARCHAR(500)             DEFAULT NULL   COMMENT '图标 URL',
    sort        INT             NOT NULL DEFAULT 0      COMMENT '排序',
    visible     TINYINT(1)      NOT NULL DEFAULT 1      COMMENT '是否显示：1 显示 / 0 隐藏',
    created_at  DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at  DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    deleted     TINYINT(1)      NOT NULL DEFAULT 0      COMMENT '逻辑删除：0 正常 / 1 已删',
    PRIMARY KEY (id),
    KEY idx_parent_id (parent_id),
    KEY idx_path (path),
    KEY idx_level_sort (level, sort)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '分类（最多 3 级）';


-- ---------- brand ----------
CREATE TABLE IF NOT EXISTS `brand` (
    id           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    name         VARCHAR(50)     NOT NULL                COMMENT '品牌名',
    logo         VARCHAR(500)             DEFAULT NULL   COMMENT 'LOGO URL',
    description  VARCHAR(500)             DEFAULT NULL   COMMENT '品牌介绍',
    sort         INT             NOT NULL DEFAULT 0      COMMENT '排序',
    enabled      TINYINT(1)      NOT NULL DEFAULT 1      COMMENT '启用 / 停用',
    created_at  DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at  DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    deleted      TINYINT(1)      NOT NULL DEFAULT 0      COMMENT '逻辑删除：0 正常 / 1 已删',
    PRIMARY KEY (id),
    UNIQUE KEY uk_name (name),
    KEY idx_enabled_sort (enabled, sort)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '品牌';


-- ---------- product ----------
CREATE TABLE IF NOT EXISTS `product` (
    id            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    name          VARCHAR(200)    NOT NULL                COMMENT '商品名称（搜索命中字段）',
    category_id   BIGINT UNSIGNED NOT NULL                COMMENT '所属分类（叶子分类）',
    brand_id      BIGINT UNSIGNED          DEFAULT NULL   COMMENT '品牌',
    main_image    VARCHAR(500)    NOT NULL                COMMENT '主图（列表封面）',
    detail        MEDIUMTEXT               DEFAULT NULL   COMMENT '富文本详情；入库前做 XSS 白名单过滤',
    status        VARCHAR(20)     NOT NULL DEFAULT 'OFF_SALE' COMMENT 'ON_SALE 上架 / OFF_SALE 下架',
    min_price     BIGINT          NOT NULL DEFAULT 0      COMMENT '冗余：默认 SKU 价格（分），供列表展示与排序',
    sales         INT             NOT NULL DEFAULT 0      COMMENT '冗余：累计销量，供列表排序与 ES 打分',
    on_sale_time  DATETIME(3)              DEFAULT NULL   COMMENT '上架时间（新品窗口与 ES 新鲜度打分）',
    created_at  DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at  DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    deleted       TINYINT(1)      NOT NULL DEFAULT 0      COMMENT '逻辑删除：0 正常 / 1 已删',
    PRIMARY KEY (id),
    KEY idx_category_status (category_id, status, deleted),
    KEY idx_brand_status (brand_id, status),
    KEY idx_status_sales (status, sales),
    KEY idx_status_on_sale_time (status, on_sale_time)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '商品 SPU';


-- ---------- product_sku ----------
CREATE TABLE IF NOT EXISTS `product_sku` (
    id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    product_id  BIGINT UNSIGNED NOT NULL                COMMENT '所属商品',
    sku_code    VARCHAR(64)     NOT NULL                COMMENT 'SKU 编码，系统自动生成 {productId}-{两位序号}',
    spec_name   VARCHAR(100)    NOT NULL DEFAULT '默认规格' COMMENT '规格名；本期每个商品仅一个默认 SKU',
    price       BIGINT          NOT NULL                COMMENT '售价（分）',
    image       VARCHAR(500)             DEFAULT NULL   COMMENT 'SKU 图；缺省用商品主图',
    sort        INT             NOT NULL DEFAULT 0      COMMENT '排序',
    status      TINYINT         NOT NULL DEFAULT 1      COMMENT '1 启用 / 0 停用',
    created_at  DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at  DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    deleted     TINYINT(1)      NOT NULL DEFAULT 0      COMMENT '逻辑删除：0 正常 / 1 已删',
    PRIMARY KEY (id),
    UNIQUE KEY uk_sku_code (sku_code),
    KEY idx_product_id (product_id, deleted)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '商品 SKU（本期单规格，保留 SKU 结构）';


-- ---------- product_image ----------
CREATE TABLE IF NOT EXISTS `product_image` (
    id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    product_id  BIGINT UNSIGNED NOT NULL                COMMENT '所属商品',
    url         VARCHAR(500)    NOT NULL                COMMENT '图片 URL（MinIO）',
    sort        INT             NOT NULL DEFAULT 0      COMMENT '排序；首图与 main_image 一致',
    created_at  DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at  DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (id),
    KEY idx_product_id (product_id, sort)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '商品图集';


-- ---------- param_template ----------
CREATE TABLE IF NOT EXISTS `param_template` (
    id        BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    name      VARCHAR(50)     NOT NULL                COMMENT '模板名，如「服装类参数」',
    sort      INT             NOT NULL DEFAULT 0      COMMENT '排序',
    enabled   TINYINT(1)      NOT NULL DEFAULT 1      COMMENT '启用 / 停用',
    created_at  DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at  DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    deleted   TINYINT(1)      NOT NULL DEFAULT 0      COMMENT '逻辑删除：0 正常 / 1 已删',
    PRIMARY KEY (id),
    UNIQUE KEY uk_name (name)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '参数模板（后台统一维护）';


-- ---------- param_item ----------
CREATE TABLE IF NOT EXISTS `param_item` (
    id           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    template_id  BIGINT UNSIGNED NOT NULL                COMMENT '所属模板',
    name         VARCHAR(50)     NOT NULL                COMMENT '参数项名',
    unit         VARCHAR(20)              DEFAULT NULL   COMMENT '单位（可选）',
    sort         INT             NOT NULL DEFAULT 0      COMMENT '排序',
    created_at  DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at  DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    deleted      TINYINT(1)      NOT NULL DEFAULT 0      COMMENT '逻辑删除：0 正常 / 1 已删',
    PRIMARY KEY (id),
    UNIQUE KEY uk_template_name (template_id, name),
    KEY idx_template_id (template_id, sort)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '参数项（如「材质」「产地」）';


-- ---------- product_param_value ----------
CREATE TABLE IF NOT EXISTS `product_param_value` (
    id             BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    product_id     BIGINT UNSIGNED NOT NULL                COMMENT '商品',
    param_item_id  BIGINT UNSIGNED NOT NULL                COMMENT '参数项（来自模板）',
    value          VARCHAR(200)    NOT NULL                COMMENT '参数值',
    sort           INT             NOT NULL DEFAULT 0      COMMENT '详情页展示顺序',
    created_at  DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at  DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_product_param (product_id, param_item_id),
    KEY idx_product_id (product_id, sort)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '商品参数值';


-- ---------- home_banner ----------
CREATE TABLE IF NOT EXISTS `home_banner` (
    id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    title       VARCHAR(100)             DEFAULT NULL   COMMENT '标题',
    image_url   VARCHAR(500)    NOT NULL                COMMENT '图片 URL',
    link_type   TINYINT         NOT NULL DEFAULT 0      COMMENT '0 无跳转 / 1 商品 / 2 分类 / 3 外链',
    link_value  VARCHAR(200)             DEFAULT NULL   COMMENT '跳转目标（商品 ID / 分类 ID / URL）',
    sort        INT             NOT NULL DEFAULT 0      COMMENT '排序',
    enabled     TINYINT(1)      NOT NULL DEFAULT 1      COMMENT '启用 / 停用',
    created_at  DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at  DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (id),
    KEY idx_enabled_sort (enabled, sort)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '首页轮播';


-- ---------- home_notice ----------
CREATE TABLE IF NOT EXISTS `home_notice` (
    id        BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    content   VARCHAR(500)    NOT NULL                COMMENT '公告内容',
    sort      INT             NOT NULL DEFAULT 0      COMMENT '排序',
    enabled   TINYINT(1)      NOT NULL DEFAULT 1      COMMENT '启用 / 停用',
    created_at  DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at  DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (id),
    KEY idx_enabled_sort (enabled, sort)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '首页公告';


-- ---------- home_nav ----------
CREATE TABLE IF NOT EXISTS `home_nav` (
    id           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    name         VARCHAR(50)     NOT NULL                COMMENT '入口名称',
    icon         VARCHAR(500)             DEFAULT NULL   COMMENT '图标 URL',
    category_id  BIGINT UNSIGNED          DEFAULT NULL   COMMENT '跳转分类',
    sort         INT             NOT NULL DEFAULT 0      COMMENT '排序',
    enabled      TINYINT(1)      NOT NULL DEFAULT 1      COMMENT '启用 / 停用',
    created_at  DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at  DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (id),
    KEY idx_enabled_sort (enabled, sort)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '首页金刚区';


-- ---------- home_floor ----------
CREATE TABLE IF NOT EXISTS `home_floor` (
    id           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    title        VARCHAR(50)     NOT NULL                COMMENT '楼层标题',
    category_id  BIGINT UNSIGNED NOT NULL                COMMENT '绑定分类（商品自动按此分类拉取）',
    sort_field   TINYINT         NOT NULL DEFAULT 1      COMMENT '该楼层独立排序维度：1 销量 / 2 上新 / 3 价格升 / 4 价格降',
    limit_size   INT             NOT NULL DEFAULT 8      COMMENT '展示数量',
    sort         INT             NOT NULL DEFAULT 0      COMMENT '楼层顺序',
    enabled      TINYINT(1)      NOT NULL DEFAULT 1      COMMENT '启用 / 停用',
    created_at  DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at  DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (id),
    KEY idx_enabled_sort (enabled, sort),
    KEY idx_category_id (category_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '首页楼层';


-- ---------- home_floor_item ----------
CREATE TABLE IF NOT EXISTS `home_floor_item` (
    id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    floor_id    BIGINT UNSIGNED NOT NULL                COMMENT '所属楼层',
    product_id  BIGINT UNSIGNED NOT NULL                COMMENT '指定商品',
    sort        INT             NOT NULL DEFAULT 0      COMMENT '置顶顺序',
    created_at  DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at  DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_floor_product (floor_id, product_id),
    KEY idx_floor_id (floor_id, sort)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '楼层手动选品（可选，为空时完全自动拉取）';

