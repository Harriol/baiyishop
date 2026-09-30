-- ============================================================================
-- 百益商城 · user 服务初始表结构
-- 依据 docs/database.md 第 3 章（baiyishop_user，8 张表）与第 1 章通用规范
-- 约定：金额用整数「分」；时间 DATETIME(3) + Asia/Shanghai；不建物理外键；逻辑删除用 deleted
-- 注意：本脚本一经执行不得修改，后续变更一律新增 V2__xxx.sql（docs/database.md 1.6）
-- ============================================================================

-- ---------- 3.1 会员账号 ----------
CREATE TABLE IF NOT EXISTS `user` (
    id             BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    username       VARCHAR(50)     NOT NULL                COMMENT '登录账号（Web 端）',
    password_hash  VARCHAR(100)             DEFAULT NULL   COMMENT 'BCrypt 哈希；禁止明文 / MD5；小程序用户可无密码',
    nickname       VARCHAR(50)              DEFAULT NULL   COMMENT '昵称',
    avatar         VARCHAR(500)             DEFAULT NULL   COMMENT '头像 URL（MinIO）',
    phone          VARCHAR(20)              DEFAULT NULL   COMMENT '手机号（接口返回需脱敏）',
    status         TINYINT         NOT NULL DEFAULT 1      COMMENT '1 正常 / 0 禁用',
    last_login_at  DATETIME(3)              DEFAULT NULL   COMMENT '最近登录时间',
    created_at     DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at     DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    deleted        TINYINT(1)      NOT NULL DEFAULT 0      COMMENT '逻辑删除：0 正常 / 1 已删',
    PRIMARY KEY (id),
    UNIQUE KEY uk_username (username),
    KEY idx_phone (phone)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '会员账号';

-- ---------- 3.2 微信绑定 ----------
CREATE TABLE IF NOT EXISTS user_wechat (
    id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    user_id     BIGINT UNSIGNED NOT NULL                COMMENT '关联 user.id',
    openid      VARCHAR(64)     NOT NULL                COMMENT '小程序 openid，同一 openid 只绑定一个账号',
    unionid     VARCHAR(64)              DEFAULT NULL   COMMENT '开放平台 unionid（有则存）',
    nickname    VARCHAR(50)              DEFAULT NULL   COMMENT '微信昵称快照',
    avatar      VARCHAR(500)             DEFAULT NULL   COMMENT '微信头像快照',
    created_at  DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at  DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    deleted     TINYINT(1)      NOT NULL DEFAULT 0      COMMENT '逻辑删除：0 正常 / 1 已删',
    PRIMARY KEY (id),
    UNIQUE KEY uk_openid (openid),
    KEY idx_user_id (user_id),
    KEY idx_unionid (unionid)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '微信绑定';

-- ---------- 3.3 收货地址 ----------
CREATE TABLE IF NOT EXISTS user_address (
    id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    user_id         BIGINT UNSIGNED NOT NULL                COMMENT '所属用户',
    receiver_name   VARCHAR(50)     NOT NULL                COMMENT '收货人',
    receiver_phone  VARCHAR(20)     NOT NULL                COMMENT '收货电话',
    province        VARCHAR(50)     NOT NULL                COMMENT '省',
    city            VARCHAR(50)     NOT NULL                COMMENT '市',
    district        VARCHAR(50)     NOT NULL                COMMENT '区 / 县',
    detail          VARCHAR(200)    NOT NULL                COMMENT '详细地址',
    is_default      TINYINT(1)      NOT NULL DEFAULT 0      COMMENT '是否默认地址',
    created_at      DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at      DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    deleted         TINYINT(1)      NOT NULL DEFAULT 0      COMMENT '逻辑删除：0 正常 / 1 已删',
    PRIMARY KEY (id),
    KEY idx_user_id (user_id, deleted)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '收货地址';

-- ---------- 3.4 后台管理员 ----------
CREATE TABLE IF NOT EXISTS admin (
    id             BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    username       VARCHAR(50)     NOT NULL                COMMENT '登录账号',
    password_hash  VARCHAR(100)    NOT NULL                COMMENT 'BCrypt 哈希',
    real_name      VARCHAR(50)              DEFAULT NULL   COMMENT '姓名',
    status         TINYINT         NOT NULL DEFAULT 1      COMMENT '1 启用 / 0 停用（停用即不可登录）',
    last_login_at  DATETIME(3)              DEFAULT NULL   COMMENT '最近登录时间',
    created_at     DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at     DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    deleted        TINYINT(1)      NOT NULL DEFAULT 0      COMMENT '逻辑删除：0 正常 / 1 已删',
    PRIMARY KEY (id),
    UNIQUE KEY uk_username (username)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '后台管理员';

-- ---------- 3.5 角色 ----------
CREATE TABLE IF NOT EXISTS `role` (
    id           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    code         VARCHAR(50)     NOT NULL                COMMENT '角色码：SUPER_ADMIN / OPERATOR / SERVICE',
    name         VARCHAR(50)     NOT NULL                COMMENT '角色名：超级管理员 / 运营 / 客服',
    description  VARCHAR(200)             DEFAULT NULL   COMMENT '说明',
    created_at   DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at   DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    deleted      TINYINT(1)      NOT NULL DEFAULT 0      COMMENT '逻辑删除：0 正常 / 1 已删',
    PRIMARY KEY (id),
    UNIQUE KEY uk_code (code)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '角色';

-- ---------- 3.6 管理员-角色 ----------
CREATE TABLE IF NOT EXISTS admin_role (
    id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    admin_id    BIGINT UNSIGNED NOT NULL                COMMENT '管理员',
    role_id     BIGINT UNSIGNED NOT NULL                COMMENT '角色',
    created_at  DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at  DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_admin_role (admin_id, role_id),
    KEY idx_role_id (role_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '管理员-角色';

-- ---------- 3.7 权限点 ----------
CREATE TABLE IF NOT EXISTS permission (
    id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    code        VARCHAR(80)     NOT NULL                COMMENT '权限标识 模块:操作，如 order:ship',
    name        VARCHAR(50)     NOT NULL                COMMENT '权限名称',
    type        TINYINT         NOT NULL DEFAULT 2      COMMENT '1 菜单 / 2 按钮（操作）',
    parent_id   BIGINT UNSIGNED NOT NULL DEFAULT 0      COMMENT '父级，0 为顶级（菜单树）',
    sort        INT             NOT NULL DEFAULT 0      COMMENT '排序',
    created_at  DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at  DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    deleted     TINYINT(1)      NOT NULL DEFAULT 0      COMMENT '逻辑删除：0 正常 / 1 已删',
    PRIMARY KEY (id),
    UNIQUE KEY uk_code (code),
    KEY idx_parent_id (parent_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '权限点';

-- ---------- 3.8 角色-权限 ----------
CREATE TABLE IF NOT EXISTS role_permission (
    id             BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    role_id        BIGINT UNSIGNED NOT NULL                COMMENT '角色',
    permission_id  BIGINT UNSIGNED NOT NULL                COMMENT '权限点',
    created_at     DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at     DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_role_permission (role_id, permission_id),
    KEY idx_permission_id (permission_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '角色-权限';
