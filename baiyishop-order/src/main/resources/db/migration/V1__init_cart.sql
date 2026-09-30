-- ============================================================================
-- 百益商城 · order 服务表结构（购物车）
-- 依据 docs/database.md 6.1（REQ-601、REQ-602）
-- ============================================================================

CREATE TABLE IF NOT EXISTS cart_item (
    id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    user_id     BIGINT UNSIGNED NOT NULL                COMMENT '用户',
    sku_id      BIGINT UNSIGNED NOT NULL                COMMENT 'SKU',
    product_id  BIGINT UNSIGNED NOT NULL                COMMENT '商品（冗余，便于校验）',
    quantity    INT             NOT NULL DEFAULT 1      COMMENT '数量（受上限校验）',
    checked     TINYINT(1)      NOT NULL DEFAULT 1      COMMENT '是否勾选（参与结算）',
    created_at  DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at  DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '变更时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_sku (user_id, sku_id),
    KEY idx_user_checked (user_id, checked)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '购物车（uk_user_sku 支持同 SKU 数量累加）';
