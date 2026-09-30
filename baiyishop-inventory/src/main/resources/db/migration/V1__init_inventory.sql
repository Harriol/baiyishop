-- ============================================================================
-- 百益商城 · inventory 服务表结构
-- 依据 docs/database.md 第 5.1 ~ 5.2、5.5 节 与第 1 章通用规范
-- 注意：本脚本一经执行不得修改，后续变更一律新增更高版本号脚本
-- ============================================================================

-- ---------- 5.1 inventory 库存（权威表）----------
CREATE TABLE IF NOT EXISTS inventory (
    id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    sku_id          BIGINT UNSIGNED NOT NULL                COMMENT 'SKU（跨服务引用 product_sku.id）',
    product_id      BIGINT UNSIGNED NOT NULL                COMMENT '商品（冗余，便于按商品查询）',
    available       INT             NOT NULL DEFAULT 0      COMMENT '可售库存（下单可占用量）',
    locked          INT             NOT NULL DEFAULT 0      COMMENT '锁定库存（已下单待付款占用）',
    warn_threshold  INT             NOT NULL DEFAULT 10     COMMENT '预警阈值',
    version         INT             NOT NULL DEFAULT 0      COMMENT '乐观锁版本',
    updated_at      DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '变更时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_sku_id (sku_id),
    KEY idx_product_id (product_id),
    KEY idx_available (available)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '库存（权威表，记录型表故不设 created_at）';

-- ---------- 5.2 inventory_flow 库存流水 ----------
CREATE TABLE IF NOT EXISTS inventory_flow (
    id                BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    biz_key           VARCHAR(80)     NOT NULL                COMMENT '幂等键：{orderNo}:{action} 或 ADJUST:{id}',
    sku_id            BIGINT UNSIGNED NOT NULL                COMMENT 'SKU',
    type              VARCHAR(20)     NOT NULL                COMMENT 'LOCK/DEDUCT/UNLOCK/ADJUST/ALLOCATE/RETURN/ROLLBACK',
    quantity          INT             NOT NULL                COMMENT '变更数量（正数，方向由类型决定）',
    before_available  INT             NOT NULL                COMMENT '变更前可售',
    after_available   INT             NOT NULL                COMMENT '变更后可售',
    before_locked     INT             NOT NULL                COMMENT '变更前锁定',
    after_locked      INT             NOT NULL                COMMENT '变更后锁定',
    reason            VARCHAR(200)             DEFAULT NULL   COMMENT '调整原因（后台调整必填）',
    operator_type     VARCHAR(20)     NOT NULL DEFAULT 'SYSTEM' COMMENT 'SYSTEM / ADMIN',
    operator_id       BIGINT UNSIGNED          DEFAULT NULL   COMMENT '操作人（后台调整时）',
    created_at        DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '发生时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_biz_key (biz_key),
    KEY idx_sku_created (sku_id, created_at),
    KEY idx_type_created (type, created_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '库存流水（uk_biz_key 是幂等落点）';

-- ---------- 5.5 stock_alert 库存预警 ----------
CREATE TABLE IF NOT EXISTS stock_alert (
    id             BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    sku_id         BIGINT UNSIGNED NOT NULL                COMMENT 'SKU',
    product_id     BIGINT UNSIGNED NOT NULL                COMMENT '商品（冗余）',
    current_stock  INT             NOT NULL                COMMENT '触发时库存',
    threshold      INT             NOT NULL                COMMENT '触发时阈值',
    status         VARCHAR(20)     NOT NULL DEFAULT 'OPEN' COMMENT 'OPEN 待处理 / CLOSED 已处理',
    handled_at     DATETIME(3)              DEFAULT NULL   COMMENT '处理时间',
    updated_at     DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '变更时间',
    PRIMARY KEY (id),
    KEY idx_status_updated (status, updated_at),
    UNIQUE KEY uk_sku_open (sku_id, status)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '库存预警';
