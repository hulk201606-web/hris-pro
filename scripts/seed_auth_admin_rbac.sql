-- 幂等 seed：为 demo 租户(100001) 的 admin 用户补齐 admin 角色 + 关键写/敏感读权限点。
-- 目标库：auth_db（MySQL 8.0）。可重复执行。
-- 依赖：admin 用户已存在（auth_user.tenant_id=100001，username='admin' 或 employee_id=200001）。

SET @tenant_id := 100001;

-- 1. admin 角色（内置模板，幂等）
INSERT INTO auth_role (id, tenant_id, code, name, builtin)
SELECT 500001, @tenant_id, 'admin', '管理员', 1
WHERE NOT EXISTS (
    SELECT 1 FROM auth_role WHERE tenant_id = @tenant_id AND code = 'admin'
);

-- 2. 角色-权限点（与后端 @RequirePerm 标注一一对应，幂等）
INSERT INTO auth_role_perm (id, tenant_id, role_id, perm_code)
SELECT p.pid, @tenant_id, r.id, p.perm_code
FROM (SELECT id FROM auth_role WHERE tenant_id = @tenant_id AND code = 'admin') r
CROSS JOIN (
    SELECT 500101 AS pid, 'org:headcount:write' AS perm_code
    UNION ALL SELECT 500102, 'org:dept:adjust'
    UNION ALL SELECT 500103, 'plt:audit:read'
    UNION ALL SELECT 500104, 'plt:config:write'
    UNION ALL SELECT 500105, 'flow:model:write'
    UNION ALL SELECT 500106, 'flow:instance:start'
    UNION ALL SELECT 500107, 'emp:lifecycle:write'
) p
WHERE NOT EXISTS (
    SELECT 1 FROM auth_role_perm x WHERE x.role_id = r.id AND x.perm_code = p.perm_code
);

-- 3. 用户-角色关联（幂等）
INSERT INTO auth_user_role (id, tenant_id, user_id, role_id)
SELECT 500201, @tenant_id, u.id, r.id
FROM (
    SELECT id FROM auth_user
    WHERE tenant_id = @tenant_id AND (username = 'admin' OR employee_id = 200001)
    LIMIT 1
) u
CROSS JOIN (SELECT id FROM auth_role WHERE tenant_id = @tenant_id AND code = 'admin') r
WHERE NOT EXISTS (
    SELECT 1 FROM auth_user_role x WHERE x.user_id = u.id AND x.role_id = r.id
);
