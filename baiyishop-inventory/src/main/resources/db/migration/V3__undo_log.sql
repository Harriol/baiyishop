-- ============================================================================
-- Seata AT 回滚日志表（docs/database.md 9.1、docs/adr/ADR-002）
-- 表结构取自 Seata 官方脚本，不自行设计；仅建在参与全局事务的 schema
-- 本 schema 的角色：
-- ============================================================================

CREATE TABLE IF NOT EXISTS undo_log (
    branch_id     BIGINT       NOT NULL                COMMENT '分支事务 ID',
    xid           VARCHAR(128) NOT NULL                COMMENT '全局事务 ID',
    context       VARCHAR(128) NOT NULL                COMMENT '序列化上下文',
    rollback_info LONGBLOB     NOT NULL                COMMENT '回滚镜像',
    log_status    INT          NOT NULL                COMMENT '0 正常 / 1 防御性回滚',
    log_created   DATETIME(6)  NOT NULL                COMMENT '创建时间',
    log_modified  DATETIME(6)  NOT NULL                COMMENT '修改时间',
    ext           VARCHAR(100)          DEFAULT NULL   COMMENT '扩展字段',
    UNIQUE KEY uk_xid_branch (xid, branch_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = 'AT 模式回滚日志（已提交事务的记录由清理任务保留 7 天后删除）';
