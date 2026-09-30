-- ============================================================================
-- 本地消息表与消费幂等表（docs/database.md 9.2 / 9.3、docs/architecture.md 6.3）
-- 作用：Seata 解决同步跨服务写的原子性，解决不了「业务已提交但消息没发出去」，
--       故发消息走事务性发件箱，消费侧靠 event_id 去重。
-- ============================================================================

CREATE TABLE IF NOT EXISTS mq_outbox (
    id             BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    event_id       VARCHAR(64)     NOT NULL                COMMENT '事件 ID（消费侧幂等键）',
    topic          VARCHAR(100)    NOT NULL                COMMENT '主题',
    tag            VARCHAR(50)              DEFAULT NULL   COMMENT '标签（事件类型）',
    biz_key        VARCHAR(80)     NOT NULL                COMMENT '业务键（如 orderNo / productId）',
    payload        TEXT            NOT NULL                COMMENT '消息体 JSON',
    status         VARCHAR(20)     NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING / SENT / FAILED',
    retry_count    INT             NOT NULL DEFAULT 0      COMMENT '重试次数',
    next_retry_at  DATETIME(3)              DEFAULT NULL   COMMENT '下次重试时间',
    created_at     DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    sent_at        DATETIME(3)              DEFAULT NULL   COMMENT '投递成功时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_event_id (event_id),
    KEY idx_status_next_retry (status, next_retry_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '本地消息表（事务性发件箱）';

CREATE TABLE IF NOT EXISTS mq_consume_log (
    id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    event_id        VARCHAR(64)     NOT NULL                COMMENT '事件 ID',
    consumer_group  VARCHAR(100)    NOT NULL                COMMENT '消费组',
    status          VARCHAR(20)     NOT NULL DEFAULT 'SUCCESS' COMMENT '处理结果',
    created_at      DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '消费时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_event_consumer (event_id, consumer_group),
    KEY idx_created (created_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '消费幂等表';
