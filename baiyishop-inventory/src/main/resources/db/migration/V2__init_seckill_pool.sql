-- ============================================================================
-- 百益商城 · inventory 服务表结构
-- 依据 docs/database.md 第 5.3 ~ 5.4 节（秒杀库存池归属 inventory，ADR-004） 与第 1 章通用规范
-- 注意：本脚本一经执行不得修改，后续变更一律新增更高版本号脚本
-- ============================================================================

-- ---------- 5.3 seckill_stock_pool 秒杀库存池 ----------
CREATE TABLE IF NOT EXISTS seckill_stock_pool (
    id               BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    activity_id      BIGINT UNSIGNED NOT NULL                COMMENT '秒杀活动（跨服务引用）',
    activity_sku_id  BIGINT UNSIGNED NOT NULL                COMMENT '活动 SKU（跨服务引用 seckill_activity_sku.id）',
    sku_id           BIGINT UNSIGNED NOT NULL                COMMENT 'SKU',
    total            INT             NOT NULL DEFAULT 0      COMMENT '划拨总量（划拨后不变）',
    remaining        INT             NOT NULL DEFAULT 0      COMMENT '秒杀池剩余',
    sold             INT             NOT NULL DEFAULT 0      COMMENT '已售出',
    version          INT             NOT NULL DEFAULT 0      COMMENT '乐观锁版本',
    updated_at       DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '变更时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_activity_sku (activity_sku_id),
    KEY idx_activity_id (activity_id),
    KEY idx_sku_id (sku_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '秒杀库存池（剩余不为负；remaining + sold = total）';

-- ---------- 5.4 seckill_stock_flow 秒杀池流水 ----------
CREATE TABLE IF NOT EXISTS seckill_stock_flow (
    id                BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    biz_key           VARCHAR(80)     NOT NULL                COMMENT '幂等键',
    pool_id           BIGINT UNSIGNED NOT NULL                COMMENT '秒杀池记录',
    activity_sku_id   BIGINT UNSIGNED NOT NULL                COMMENT '活动 SKU',
    type              VARCHAR(20)     NOT NULL                COMMENT 'ALLOCATE/DEDUCT/RETURN/ROLLBACK',
    quantity          INT             NOT NULL                COMMENT '数量',
    before_remaining  INT             NOT NULL                COMMENT '变更前剩余',
    after_remaining   INT             NOT NULL                COMMENT '变更后剩余',
    created_at        DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '发生时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_biz_key (biz_key),
    KEY idx_activity_sku_created (activity_sku_id, created_at),
    KEY idx_type_created (type, created_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '秒杀池流水';
