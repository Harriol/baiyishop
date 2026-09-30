-- ============================================================================
-- 百益商城 · order 服务表结构（订单）
-- 依据 docs/database.md 6.2 ~ 6.6（REQ-701 ~ REQ-708）
-- 注意：order 是 MySQL 关键字，建表与查询都必须加反引号
-- ============================================================================

-- ---------- 6.2 order 订单主表 ----------
CREATE TABLE IF NOT EXISTS `order` (
    id                BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    order_no          VARCHAR(32)     NOT NULL                COMMENT '全局唯一订单号（唯一索引兜底）',
    user_id           BIGINT UNSIGNED NOT NULL                COMMENT '下单用户（必须登录）',
    status            VARCHAR(20)     NOT NULL DEFAULT 'PENDING_PAYMENT' COMMENT '待付款/待发货/待收货/已完成/已取消',
    source            VARCHAR(20)     NOT NULL DEFAULT 'CART' COMMENT 'CART 购物车结算 / BUY_NOW 立即购买 / SECKILL 秒杀',
    total_amount      BIGINT          NOT NULL DEFAULT 0      COMMENT '商品金额合计（分）',
    freight_amount    BIGINT          NOT NULL DEFAULT 0      COMMENT '运费（分）；全场包邮恒为 0',
    pay_amount        BIGINT          NOT NULL DEFAULT 0      COMMENT '应付金额（分）',
    pay_type          VARCHAR(20)              DEFAULT NULL   COMMENT '支付渠道：WECHAT / ALIPAY',
    receiver_name     VARCHAR(50)     NOT NULL                COMMENT '地址快照：收货人',
    receiver_phone    VARCHAR(20)     NOT NULL                COMMENT '地址快照：电话',
    receiver_address  VARCHAR(300)    NOT NULL                COMMENT '地址快照：省市区 + 详细地址',
    remark            VARCHAR(200)             DEFAULT NULL   COMMENT '用户备注',
    cancel_reason     VARCHAR(200)             DEFAULT NULL   COMMENT '取消原因',
    timeout_at        DATETIME(3)              DEFAULT NULL   COMMENT '支付超时时间 = 创建时间 + 15 分钟',
    pay_time          DATETIME(3)              DEFAULT NULL   COMMENT '支付时间',
    ship_time         DATETIME(3)              DEFAULT NULL   COMMENT '发货时间',
    tracking_no       VARCHAR(64)              DEFAULT NULL   COMMENT '运单号（仅记录，不追踪轨迹）',
    auto_receive_at   DATETIME(3)              DEFAULT NULL   COMMENT '自动确认收货时间 = 发货时间 + 7 天',
    receive_time      DATETIME(3)              DEFAULT NULL   COMMENT '确认收货时间',
    finish_time       DATETIME(3)              DEFAULT NULL   COMMENT '完成时间',
    cancel_time       DATETIME(3)              DEFAULT NULL   COMMENT '取消时间',
    created_at        DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at        DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '变更时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_order_no (order_no),
    KEY idx_user_status_created (user_id, status, created_at),
    KEY idx_status_timeout (status, timeout_at),
    KEY idx_status_auto_receive (status, auto_receive_at),
    KEY idx_status_created (status, created_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '订单主表（状态流转只走条件更新，保证幂等）';

-- ---------- 6.3 order_item 订单明细（含商品快照）----------
CREATE TABLE IF NOT EXISTS order_item (
    id             BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    order_id       BIGINT UNSIGNED NOT NULL                COMMENT '订单',
    order_no       VARCHAR(32)     NOT NULL                COMMENT '订单号（冗余）',
    product_id     BIGINT UNSIGNED NOT NULL                COMMENT '商品 ID',
    sku_id         BIGINT UNSIGNED NOT NULL                COMMENT 'SKU ID',
    product_name   VARCHAR(200)    NOT NULL                COMMENT '快照：商品名称',
    sku_name       VARCHAR(100)    NOT NULL                COMMENT '快照：规格名',
    product_image  VARCHAR(500)    NOT NULL                COMMENT '快照：商品图',
    unit_price     BIGINT          NOT NULL                COMMENT '快照：下单时单价（分）',
    quantity       INT             NOT NULL                COMMENT '数量',
    total_amount   BIGINT          NOT NULL                COMMENT '小计（分）= unit_price × quantity',
    created_at     DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    PRIMARY KEY (id),
    KEY idx_order_id (order_id),
    KEY idx_order_no (order_no),
    KEY idx_sku_id (sku_id),
    KEY idx_product_id (product_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '订单明细（快照保证历史订单不受商品变更影响）';

-- ---------- 6.4 order_status_log 状态流转留痕 ----------
CREATE TABLE IF NOT EXISTS order_status_log (
    id             BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    order_id       BIGINT UNSIGNED NOT NULL                COMMENT '订单',
    order_no       VARCHAR(32)     NOT NULL                COMMENT '订单号（冗余）',
    from_status    VARCHAR(20)              DEFAULT NULL   COMMENT '原状态（创建时为 NULL）',
    to_status      VARCHAR(20)     NOT NULL                COMMENT '目标状态',
    operator_type  VARCHAR(20)     NOT NULL                COMMENT 'SYSTEM / USER / ADMIN',
    operator_id    BIGINT UNSIGNED          DEFAULT NULL   COMMENT '操作人 ID',
    reason         VARCHAR(200)             DEFAULT NULL   COMMENT '原因（如「超时未支付」）',
    created_at     DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '发生时间',
    PRIMARY KEY (id),
    KEY idx_order_id (order_id, created_at),
    KEY idx_order_no (order_no)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '订单状态流转留痕';

-- ---------- 6.5 order_note 后台订单备注 ----------
CREATE TABLE IF NOT EXISTS order_note (
    id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    order_id    BIGINT UNSIGNED NOT NULL                COMMENT '订单',
    admin_id    BIGINT UNSIGNED NOT NULL                COMMENT '备注管理员',
    admin_name  VARCHAR(50)     NOT NULL                COMMENT '备注人姓名（快照）',
    content     VARCHAR(500)    NOT NULL                COMMENT '备注内容',
    created_at  DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '备注时间',
    PRIMARY KEY (id),
    KEY idx_order_id (order_id, created_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '后台订单备注（客服的全部写权限）';

-- ---------- 6.6 order_request 下单请求幂等表 ----------
CREATE TABLE IF NOT EXISTS order_request (
    id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    request_id  VARCHAR(64)     NOT NULL                COMMENT '前端生成的请求 ID',
    user_id     BIGINT UNSIGNED NOT NULL                COMMENT '用户',
    order_no    VARCHAR(32)     NOT NULL                COMMENT '首次成功生成的订单号',
    created_at  DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_request_id (request_id),
    KEY idx_user_id (user_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '下单请求幂等（重复提交返回首次订单号）';
