-- ============================================================================
-- 百益商城 · payment 服务表结构
-- 依据 docs/database.md 7.1 ~ 7.2（REQ-801 ~ REQ-803）
-- ============================================================================

-- ---------- 7.1 payment 支付单 ----------
CREATE TABLE IF NOT EXISTS payment (
    id                BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    payment_no        VARCHAR(32)     NOT NULL                COMMENT '支付单号（唯一）',
    order_no          VARCHAR(32)     NOT NULL                COMMENT '关联订单号（跨服务引用）',
    user_id           BIGINT UNSIGNED NOT NULL                COMMENT '付款用户',
    channel           VARCHAR(20)     NOT NULL                COMMENT 'WECHAT / ALIPAY（本期为模拟实现）',
    amount            BIGINT          NOT NULL                COMMENT '支付金额（分），必须与订单 pay_amount 一致',
    status            VARCHAR(20)     NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING / SUCCESS / FAILED / CLOSED',
    channel_trade_no  VARCHAR(64)              DEFAULT NULL   COMMENT '渠道流水号（回调后回填）',
    pay_time          DATETIME(3)              DEFAULT NULL   COMMENT '支付成功时间',
    expire_at         DATETIME(3)              DEFAULT NULL   COMMENT '支付过期时间（与订单 15 分钟超时对齐）',
    created_at        DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at        DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '变更时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_payment_no (payment_no),
    KEY idx_order_no (order_no),
    KEY idx_status_expire (status, expire_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '支付单';

-- ---------- 7.2 payment_callback_log 回调日志 ----------
CREATE TABLE IF NOT EXISTS payment_callback_log (
    id                BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    channel           VARCHAR(20)     NOT NULL                COMMENT '渠道',
    channel_trade_no  VARCHAR(64)     NOT NULL                COMMENT '渠道流水号',
    payment_no        VARCHAR(32)              DEFAULT NULL   COMMENT '匹配到的支付单（未匹配则 NULL）',
    raw_body          TEXT                     DEFAULT NULL   COMMENT '回调报文（脱敏后存储，不含密钥）',
    sign_verified     TINYINT(1)      NOT NULL DEFAULT 0      COMMENT '验签是否通过',
    process_result    VARCHAR(200)             DEFAULT NULL   COMMENT '处理结果描述',
    created_at        DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '收到时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_channel_trade (channel, channel_trade_no),
    KEY idx_payment_no (payment_no),
    KEY idx_created (created_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '支付回调日志（uk_channel_trade 是回调幂等落点）';
