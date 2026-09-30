-- ============================================================================
-- 百益商城 · user 服务字典数据：三角色与权限点
-- 依据 docs/database.md 3.8 与 docs/adr/ADR-003（RBAC 三角色）
-- 使用可重复脚本 R__：内容变更时 Flyway 会重新执行，因此必须写成幂等语句
-- ============================================================================

INSERT INTO `role` (code, name, description)
VALUES ('SUPER_ADMIN', '超级管理员', '全部权限，含管理员与角色管理'),
       ('OPERATOR', '运营', '商品 / 分类 / 库存 / 首页配置 / 秒杀活动 / 订单（含发货、备注）'),
       ('SERVICE', '客服', '仅订单查询与备注，不可发货、不可退款')
ON DUPLICATE KEY UPDATE name = VALUES(name), description = VALUES(description);

INSERT INTO permission (code, name, type, parent_id, sort)
VALUES ('admin:read',      '管理员查看',   2, 0, 10),
       ('admin:write',     '管理员管理',   2, 0, 11),
       ('role:read',       '角色查看',     2, 0, 12),
       ('role:write',      '角色管理',     2, 0, 13),
       ('category:read',   '分类查看',     2, 0, 20),
       ('category:write',  '分类管理',     2, 0, 21),
       ('brand:read',      '品牌查看',     2, 0, 22),
       ('brand:write',     '品牌管理',     2, 0, 23),
       ('product:read',    '商品查看',     2, 0, 30),
       ('product:write',   '商品管理',     2, 0, 31),
       ('param:read',      '参数查看',     2, 0, 32),
       ('param:write',     '参数管理',     2, 0, 33),
       ('home:read',       '首页配置查看', 2, 0, 40),
       ('home:write',      '首页配置管理', 2, 0, 41),
       ('inventory:read',  '库存查看',     2, 0, 50),
       ('inventory:write', '库存管理',     2, 0, 51),
       ('seckill:read',    '秒杀查看',     2, 0, 60),
       ('seckill:write',   '秒杀管理',     2, 0, 61),
       ('order:read',      '订单查询',     2, 0, 70),
       ('order:note',      '订单备注',     2, 0, 71),
       ('order:ship',      '订单发货',     2, 0, 72)
ON DUPLICATE KEY UPDATE name = VALUES(name), type = VALUES(type), sort = VALUES(sort);

-- 超管：全部权限
INSERT INTO role_permission (role_id, permission_id)
SELECT r.id, p.id FROM `role` r CROSS JOIN permission p
WHERE r.code = 'SUPER_ADMIN' AND r.deleted = 0 AND p.deleted = 0
ON DUPLICATE KEY UPDATE role_id = role_id;

-- 运营：除管理员与角色外的业务权限
INSERT INTO role_permission (role_id, permission_id)
SELECT r.id, p.id FROM `role` r CROSS JOIN permission p
WHERE r.code = 'OPERATOR' AND p.code NOT IN ('admin:read', 'admin:write', 'role:read', 'role:write')
  AND r.deleted = 0 AND p.deleted = 0
ON DUPLICATE KEY UPDATE role_id = role_id;

-- 客服：仅订单查询与备注（R5-Q3）
INSERT INTO role_permission (role_id, permission_id)
SELECT r.id, p.id FROM `role` r CROSS JOIN permission p
WHERE r.code = 'SERVICE' AND p.code IN ('order:read', 'order:note')
  AND r.deleted = 0 AND p.deleted = 0
ON DUPLICATE KEY UPDATE role_id = role_id;
