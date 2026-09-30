-- ============================================================================
-- 百益商城 · seckill 服务表结构
-- 依据 docs/database.md 8.1 ~ 8.3（REQ-901 ~ REQ-906）
-- 秒杀链路不参与 Seata 全局事务（Redis 与 MQ 不在事务内，ADR-002 / ADR-008），
-- 故本 schema 无 undo_log；可靠性靠 mq_outbox（V2）+ 消费幂等 + 对账
-- ============================================================================

-- ---------- 8.1 seckill_activity 秒杀活动 ----------
CREATE TABLE IF NOT EXISTS seckill_activity (
    id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    name        VARCHAR(100)    NOT NULL                COMMENT '活动名称',
    start_time  DATETIME(3)     NOT NULL                COMMENT '开始时间（后台设定）',
    end_time    DATETIME(3)     NOT NULL                COMMENT '结束时间（后台设定）',
    status      VARCHAR(20)     NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT / NOT_STARTED / RUNNING / ENDED',
    created_by  BIGINT UNSIGNED          DEFAULT NULL   COMMENT '创建管理员',
    created_at  DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at  DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '变更时间',
    PRIMARY KEY (id),
    KEY idx_status_start (status, start_time),
    KEY idx_end_time (end_time)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '秒杀活动（end_time <= start_time 需被服务层拒绝）';

-- ---------- 8.2 seckill_activity_sku 活动商品 ----------
CREATE TABLE IF NOT EXISTS seckill_activity_sku (
    id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    activity_id     BIGINT UNSIGNED NOT NULL                COMMENT '活动',
    sku_id          BIGINT UNSIGNED NOT NULL                COMMENT 'SKU（跨服务引用）',
    product_id      BIGINT UNSIGNED NOT NULL                COMMENT '商品（冗余）',
    seckill_price   BIGINT          NOT NULL                COMMENT '秒杀价（分）',
    original_price  BIGINT          NOT NULL                COMMENT '原价（分，展示划线价）',
    alloc_stock     INT             NOT NULL DEFAULT 0      COMMENT '划拨量（意图记录，权威值在 seckill_stock_pool.total）',
    limit_per_user  INT             NOT NULL DEFAULT 1      COMMENT '每人限购数（后台可配置）',
    sort            INT             NOT NULL DEFAULT 0      COMMENT '排序',
    created_at      DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at      DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '变更时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_activity_sku (activity_id, sku_id),
    KEY idx_activity_id (activity_id, sort),
    KEY idx_sku_id (sku_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '秒杀活动商品';

-- ---------- 8.3 seckill_record 抢购记录与结果 ----------
CREATE TABLE IF NOT EXISTS seckill_record (
    id               BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    ticket_id        VARCHAR(40)     NOT NULL                COMMENT '排队票据（唯一），前端凭此轮询结果',
    activity_id      BIGINT UNSIGNED NOT NULL                COMMENT '活动',
    activity_sku_id  BIGINT UNSIGNED NOT NULL                COMMENT '活动 SKU',
    user_id          BIGINT UNSIGNED NOT NULL                COMMENT '用户',
    request_id       VARCHAR(64)     NOT NULL                COMMENT '前端请求 ID（幂等）',
    quantity         INT             NOT NULL DEFAULT 1      COMMENT '抢购数量',
    status           VARCHAR(20)     NOT NULL DEFAULT 'QUEUED' COMMENT 'QUEUED / SUCCESS / FAILED',
    fail_reason      VARCHAR(50)              DEFAULT NULL   COMMENT 'SOLD_OUT / LIMIT_EXCEEDED / ACTIVITY_NOT_STARTED / ACTIVITY_ENDED / SYSTEM_ERROR',
    order_no         VARCHAR(32)              DEFAULT NULL   COMMENT '成功后的订单号',
    created_at       DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at       DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '变更时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_ticket_id (ticket_id),
    UNIQUE KEY uk_user_request (activity_sku_id, user_id, request_id),
    KEY idx_user_activity (user_id, activity_id, status),
    KEY idx_status_created (status, created_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '抢购记录（uk_user_request 保证同一用户重复请求幂等）';
