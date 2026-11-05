-- =====================================================================
-- 智云 HR (hris-pro) 数据库基线 DDL - PostgreSQL 15+
-- 版本: v1.0  日期: 2026-10-15
-- 说明:
--   1) 本文件为 11 个服务逻辑库的字段级完整 DDL (不含 Flowable ACT_* 引擎表,
--      由 Flowable 7.2.x 按官方 schema 自动创建)。
--   2) 全局规范: 主键 BIGINT 雪花ID(应用层生成); 公共列 tenant_id/created_by/
--      updated_by/created_at/updated_at/deleted/version; 唯一约束 (tenant_id, xxx)
--      复合前缀; 金额 NUMERIC(18,4); 时间存 UTC 且为 TIMESTAMP(3) 无时区。
--   3) 会话时区约定: 连接须设 TimeZone=UTC (JDBC: options=-c TimeZone=UTC),
--      保证 CURRENT_TIMESTAMP 默认值落 UTC; updated_at 由 MyBatis-Plus 应用层填充。
--   4) 大小写语义: 应用层对编码/账号类字段(如 code/username/emp_no)写路径统一
--      trim+lower 规范化; 关键登录字段 auth_user.username 另加 lower() 函数唯一索引
--      兜底, 与 MySQL utf8mb4_0900_ai_ci 语义对齐。
--   5) 分片/广播/单表由 ShardingSphere 路由规则决定(逻辑表结构一致); att_punch_record
--      与 sys_audit_log 月度分区由平台调度任务维护(此处给示例分区)。
--   6) 不建物理外键(分片+软删+跨库一致性), 逻辑外键在列注释标注。
-- =====================================================================

-- =====================================================================
-- 域 1: auth_db 认证授权
-- =====================================================================
CREATE SCHEMA IF NOT EXISTS auth_db;
SET search_path TO auth_db;

-- 租户主档
CREATE TABLE auth_tenant (
  id            BIGINT       NOT NULL,                            -- 主键: 雪花ID(含租户位段)
  code          VARCHAR(64)  NOT NULL,                            -- 租户编码(登录域/子域名标识)
  name          VARCHAR(128) NOT NULL,                            -- 租户名称
  storage_mode  VARCHAR(16)  NOT NULL DEFAULT 'SHARED',           -- 存储模式 SHARED=共享库 DEDICATED=专属数据源
  plan          VARCHAR(32)  NOT NULL DEFAULT 'STARTER',          -- 订阅套餐
  status        SMALLINT     NOT NULL DEFAULT 1,                  -- 状态 1=正常 0=停用 2=过期
  timezone      VARCHAR(64)  NOT NULL DEFAULT 'Asia/Shanghai',    -- 租户时区(展示用)
  domain        VARCHAR(128),                                     -- 租户独立域名
  created_by    BIGINT       NOT NULL DEFAULT 0,                  -- 创建人ID,0=系统
  updated_by    BIGINT       NOT NULL DEFAULT 0,                  -- 更新人ID
  created_at    TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,  -- 创建时间(UTC)
  updated_at    TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,  -- 更新时间(UTC,应用层维护)
  deleted       SMALLINT     NOT NULL DEFAULT 0,                  -- 逻辑删除 0=否 1=是
  version       INTEGER      NOT NULL DEFAULT 0,                  -- 乐观锁版本号
  CONSTRAINT pk_auth_tenant PRIMARY KEY (id),
  CONSTRAINT uk_auth_tenant_code UNIQUE (code),
  CONSTRAINT uk_auth_tenant_domain UNIQUE (domain)
);

-- 用户账号(一人一账号, 离职禁用)
CREATE TABLE auth_user (
  id            BIGINT       NOT NULL,                            -- 主键: 雪花ID
  tenant_id     BIGINT       NOT NULL,                            -- 租户ID
  employee_id   BIGINT       NOT NULL,                            -- 员工ID(逻辑外键 emp_employee.id)
  username      VARCHAR(64)  NOT NULL,                            -- 登录用户名
  pwd_hash      VARCHAR(255) NOT NULL,                            -- 密码哈希(Argon2id)
  mfa_secret    VARCHAR(64),                                      -- TOTP 密钥(启用MFA后写入,密文)
  status        SMALLINT     NOT NULL DEFAULT 1,                  -- 状态 1=正常 0=禁用(离职联动)
  created_by    BIGINT       NOT NULL DEFAULT 0,                  -- 创建人ID,0=系统
  updated_by    BIGINT       NOT NULL DEFAULT 0,                  -- 更新人ID
  created_at    TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,  -- 创建时间(UTC)
  updated_at    TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,  -- 更新时间(UTC,应用层维护)
  deleted       SMALLINT     NOT NULL DEFAULT 0,                  -- 逻辑删除 0=否 1=是
  version       INTEGER      NOT NULL DEFAULT 0,                  -- 乐观锁版本号
  CONSTRAINT pk_auth_user PRIMARY KEY (id),
  CONSTRAINT uk_auth_user_employee UNIQUE (tenant_id, employee_id)
);
CREATE UNIQUE INDEX uk_auth_user_username ON auth_user (tenant_id, lower(username));
CREATE INDEX idx_auth_user_status ON auth_user (tenant_id, status);

-- 租户角色(RBAC)
CREATE TABLE auth_role (
  id            BIGINT       NOT NULL,                            -- 主键: 雪花ID
  tenant_id     BIGINT       NOT NULL,                            -- 租户ID
  code          VARCHAR(64)  NOT NULL,                            -- 角色编码(内置模板或自定义)
  name          VARCHAR(64)  NOT NULL,                            -- 角色名称
  builtin       SMALLINT     NOT NULL DEFAULT 0,                  -- 是否内置模板 1=内置 0=自定义
  created_by    BIGINT       NOT NULL DEFAULT 0,                  -- 创建人ID,0=系统
  updated_by    BIGINT       NOT NULL DEFAULT 0,                  -- 更新人ID
  created_at    TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,  -- 创建时间(UTC)
  updated_at    TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,  -- 更新时间(UTC,应用层维护)
  deleted       SMALLINT     NOT NULL DEFAULT 0,                  -- 逻辑删除 0=否 1=是
  version       INTEGER      NOT NULL DEFAULT 0,                  -- 乐观锁版本号
  CONSTRAINT pk_auth_role PRIMARY KEY (id),
  CONSTRAINT uk_auth_role_code UNIQUE (tenant_id, code)
);

-- 角色-权限点
CREATE TABLE auth_role_perm (
  id            BIGINT       NOT NULL,                            -- 主键: 雪花ID
  tenant_id     BIGINT       NOT NULL,                            -- 租户ID
  role_id       BIGINT       NOT NULL,                            -- 角色ID(逻辑外键 auth_role.id)
  perm_code     VARCHAR(128) NOT NULL,                            -- 权限点编码(模块:资源:动作)
  created_by    BIGINT       NOT NULL DEFAULT 0,                  -- 创建人ID,0=系统
  updated_by    BIGINT       NOT NULL DEFAULT 0,                  -- 更新人ID
  created_at    TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,  -- 创建时间(UTC)
  updated_at    TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,  -- 更新时间(UTC,应用层维护)
  deleted       SMALLINT     NOT NULL DEFAULT 0,                  -- 逻辑删除 0=否 1=是
  version       INTEGER      NOT NULL DEFAULT 0,                  -- 乐观锁版本号
  CONSTRAINT pk_auth_role_perm PRIMARY KEY (id),
  CONSTRAINT uk_auth_role_perm UNIQUE (role_id, perm_code)
);

-- 用户-角色
CREATE TABLE auth_user_role (
  id            BIGINT       NOT NULL,                            -- 主键: 雪花ID
  tenant_id     BIGINT       NOT NULL,                            -- 租户ID
  user_id       BIGINT       NOT NULL,                            -- 用户ID(逻辑外键 auth_user.id)
  role_id       BIGINT       NOT NULL,                            -- 角色ID(逻辑外键 auth_role.id)
  created_by    BIGINT       NOT NULL DEFAULT 0,                  -- 创建人ID,0=系统
  updated_by    BIGINT       NOT NULL DEFAULT 0,                  -- 更新人ID
  created_at    TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,  -- 创建时间(UTC)
  updated_at    TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,  -- 更新时间(UTC,应用层维护)
  deleted       SMALLINT     NOT NULL DEFAULT 0,                  -- 逻辑删除 0=否 1=是
  version       INTEGER      NOT NULL DEFAULT 0,                  -- 乐观锁版本号
  CONSTRAINT pk_auth_user_role PRIMARY KEY (id),
  CONSTRAINT uk_auth_user_role UNIQUE (user_id, role_id)
);

-- 数据权限规则
CREATE TABLE auth_data_scope (
  id            BIGINT       NOT NULL,                            -- 主键: 雪花ID
  tenant_id     BIGINT       NOT NULL,                            -- 租户ID
  role_id       BIGINT       NOT NULL,                            -- 角色ID(逻辑外键 auth_role.id)
  scope_type    VARCHAR(16)  NOT NULL,                            -- 范围 SELF/DEPT/DEPT_SUB/CUSTOM/ALL
  dept_ids      JSONB,                                            -- 自定义范围部门ID集合(CUSTOM时)
  created_by    BIGINT       NOT NULL DEFAULT 0,                  -- 创建人ID,0=系统
  updated_by    BIGINT       NOT NULL DEFAULT 0,                  -- 更新人ID
  created_at    TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,  -- 创建时间(UTC)
  updated_at    TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,  -- 更新时间(UTC,应用层维护)
  deleted       SMALLINT     NOT NULL DEFAULT 0,                  -- 逻辑删除 0=否 1=是
  version       INTEGER      NOT NULL DEFAULT 0,                  -- 乐观锁版本号
  CONSTRAINT pk_auth_data_scope PRIMARY KEY (id)
);
CREATE INDEX idx_auth_data_scope_role ON auth_data_scope (tenant_id, role_id);

-- OAuth2 客户端注册 (Spring Authorization Server 官方表, 平台级, 不带 tenant_id)
CREATE TABLE oauth2_registered_client (
  id                            VARCHAR(100)  NOT NULL,           -- 注册ID
  client_id                     VARCHAR(100)  NOT NULL,           -- 客户端ID
  client_id_issued_at           TIMESTAMP(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP, -- 客户端ID签发时间
  client_secret                 VARCHAR(200),                     -- 客户端密钥(密文)
  client_secret_expires_at      TIMESTAMP(3),                     -- 客户端密钥过期时间
  client_name                   VARCHAR(200)  NOT NULL,           -- 客户端名称
  client_authentication_methods VARCHAR(1000) NOT NULL,           -- 认证方法列表
  authorization_grant_types     VARCHAR(1000) NOT NULL,           -- 授权类型列表
  redirect_uris                 VARCHAR(1000),                    -- 重定向URI列表
  post_logout_redirect_uris     VARCHAR(1000),                    -- 登出重定向URI列表
  scopes                        VARCHAR(1000) NOT NULL,           -- 授权范围列表
  client_settings               VARCHAR(2000) NOT NULL,           -- 客户端设置(JSON)
  token_settings                VARCHAR(2000) NOT NULL,           -- 令牌设置(JSON)
  CONSTRAINT pk_oauth2_registered_client PRIMARY KEY (id)
);

-- OAuth2 授权/token 存储 (官方表)
CREATE TABLE oauth2_authorization (
  id                            VARCHAR(100)  NOT NULL,           -- 授权ID
  registered_client_id          VARCHAR(100)  NOT NULL,           -- 客户端注册ID
  principal_name                VARCHAR(200)  NOT NULL,           -- 主体名(用户)
  authorization_grant_type      VARCHAR(100)  NOT NULL,           -- 授权类型
  authorized_scopes             VARCHAR(1000),                    -- 已授权范围
  attributes                    BYTEA,                            -- 附加属性(序列化)
  state                         VARCHAR(500),                     -- state 参数
  authorization_code_value      BYTEA,                            -- 授权码值
  authorization_code_issued_at  TIMESTAMP(3),                     -- 授权码签发时间
  authorization_code_expires_at TIMESTAMP(3),                     -- 授权码过期时间
  authorization_code_metadata   BYTEA,                            -- 授权码元数据
  access_token_value            BYTEA,                            -- 访问令牌值
  access_token_issued_at        TIMESTAMP(3),                     -- 访问令牌签发时间
  access_token_expires_at       TIMESTAMP(3),                     -- 访问令牌过期时间
  access_token_metadata         BYTEA,                            -- 访问令牌元数据
  access_token_type             VARCHAR(100),                     -- 访问令牌类型
  access_token_scopes           VARCHAR(1000),                    -- 访问令牌范围
  oidc_id_token_value           BYTEA,                            -- OIDC ID令牌值
  oidc_id_token_issued_at       TIMESTAMP(3),                     -- ID令牌签发时间
  oidc_id_token_expires_at      TIMESTAMP(3),                     -- ID令牌过期时间
  oidc_id_token_metadata        BYTEA,                            -- ID令牌元数据
  refresh_token_value           BYTEA,                            -- 刷新令牌值
  refresh_token_issued_at       TIMESTAMP(3),                     -- 刷新令牌签发时间
  refresh_token_expires_at      TIMESTAMP(3),                     -- 刷新令牌过期时间
  refresh_token_metadata        BYTEA,                            -- 刷新令牌元数据
  user_code_value               BYTEA,                            -- 用户码值(设备流)
  user_code_issued_at           TIMESTAMP(3),                     -- 用户码签发时间
  user_code_expires_at          TIMESTAMP(3),                     -- 用户码过期时间
  user_code_metadata            BYTEA,                            -- 用户码元数据
  device_code_value             BYTEA,                            -- 设备码值
  device_code_issued_at         TIMESTAMP(3),                     -- 设备码签发时间
  device_code_expires_at        TIMESTAMP(3),                     -- 设备码过期时间
  device_code_metadata          BYTEA,                            -- 设备码元数据
  CONSTRAINT pk_oauth2_authorization PRIMARY KEY (id)
);
CREATE INDEX idx_oauth2_auth_client_principal ON oauth2_authorization (registered_client_id, principal_name);

-- OAuth2 授权同意 (官方表)
CREATE TABLE oauth2_authorization_consent (
  registered_client_id VARCHAR(100)  NOT NULL,                    -- 客户端注册ID
  principal_name       VARCHAR(200)  NOT NULL,                    -- 主体名(用户)
  authorities          VARCHAR(1000) NOT NULL,                    -- 已同意权限列表
  CONSTRAINT pk_oauth2_authorization_consent PRIMARY KEY (registered_client_id, principal_name)
);

-- =====================================================================
-- 域 2: org_db 组织岗位
-- =====================================================================
CREATE SCHEMA IF NOT EXISTS org_db;
SET search_path TO org_db;

-- 法人主体
CREATE TABLE org_legal_entity (
  id            BIGINT       NOT NULL,                            -- 主键: 雪花ID
  tenant_id     BIGINT       NOT NULL,                            -- 租户ID
  name          VARCHAR(128) NOT NULL,                            -- 法人名称
  uscc          VARCHAR(32)  NOT NULL,                            -- 统一社会信用代码
  currency      VARCHAR(8)   NOT NULL DEFAULT 'CNY',              -- 记账本位币
  status        SMALLINT     NOT NULL DEFAULT 1,                  -- 状态 1=有效 0=失效
  created_by    BIGINT       NOT NULL DEFAULT 0,                  -- 创建人ID,0=系统
  updated_by    BIGINT       NOT NULL DEFAULT 0,                  -- 更新人ID
  created_at    TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,  -- 创建时间(UTC)
  updated_at    TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,  -- 更新时间(UTC,应用层维护)
  deleted       SMALLINT     NOT NULL DEFAULT 0,                  -- 逻辑删除 0=否 1=是
  version       INTEGER      NOT NULL DEFAULT 0,                  -- 乐观锁版本号
  CONSTRAINT pk_org_legal_entity PRIMARY KEY (id),
  CONSTRAINT uk_org_legal_entity_uscc UNIQUE (tenant_id, uscc)
);

-- 部门树(物化路径+版本化生效)
CREATE TABLE org_dept (
  id               BIGINT       NOT NULL,                         -- 主键: 雪花ID
  tenant_id        BIGINT       NOT NULL,                         -- 租户ID
  parent_id        BIGINT       NOT NULL DEFAULT 0,               -- 父部门ID,0=根
  path             VARCHAR(255) NOT NULL,                         -- 物化路径(如 1.12.130)
  name             VARCHAR(128) NOT NULL,                         -- 部门名称
  code             VARCHAR(64)  NOT NULL,                         -- 部门编码
  manager_emp_id   BIGINT,                                        -- 部门负责人员工ID(逻辑外键 emp_employee.id)
  effective_from   DATE         NOT NULL,                         -- 生效开始日
  effective_to     DATE,                                          -- 生效结束日(NULL=现行)
  status           SMALLINT     NOT NULL DEFAULT 1,               -- 状态 1=有效 0=失效
  sort_order       INTEGER      NOT NULL DEFAULT 0,               -- 兄弟排序号(ORG-F-002 深化拖拽排序)
  created_by       BIGINT       NOT NULL DEFAULT 0,               -- 创建人ID,0=系统
  updated_by       BIGINT       NOT NULL DEFAULT 0,               -- 更新人ID
  created_at       TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP, -- 创建时间(UTC)
  updated_at       TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP, -- 更新时间(UTC,应用层维护)
  deleted          SMALLINT     NOT NULL DEFAULT 0,               -- 逻辑删除 0=否 1=是
  version          INTEGER      NOT NULL DEFAULT 0,               -- 乐观锁版本号
  CONSTRAINT pk_org_dept PRIMARY KEY (id),
  CONSTRAINT uk_org_dept_code UNIQUE (tenant_id, code)
);
CREATE INDEX idx_org_dept_parent ON org_dept (tenant_id, parent_id);
CREATE INDEX idx_org_dept_path ON org_dept (tenant_id, path);

-- 岗位
CREATE TABLE org_position (
  id            BIGINT       NOT NULL,                            -- 主键: 雪花ID
  tenant_id     BIGINT       NOT NULL,                            -- 租户ID
  name          VARCHAR(64)  NOT NULL,                            -- 岗位名称
  code          VARCHAR(64)  NOT NULL,                            -- 岗位编码
  status        SMALLINT     NOT NULL DEFAULT 1,                  -- 状态 1=有效 0=失效
  job_family    VARCHAR(32),                                      -- 岗位序列(ORG-F-006 深化, 如 TECH/SALES/FUNC, NULL=未分类)
  created_by    BIGINT       NOT NULL DEFAULT 0,                  -- 创建人ID,0=系统
  updated_by    BIGINT       NOT NULL DEFAULT 0,                  -- 更新人ID
  created_at    TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,  -- 创建时间(UTC)
  updated_at    TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,  -- 更新时间(UTC,应用层维护)
  deleted       SMALLINT     NOT NULL DEFAULT 0,                  -- 逻辑删除 0=否 1=是
  version       INTEGER      NOT NULL DEFAULT 0,                  -- 乐观锁版本号
  CONSTRAINT pk_org_position PRIMARY KEY (id),
  CONSTRAINT uk_org_position_code UNIQUE (tenant_id, code)
);

-- 岗位×部门挂靠(ORG-F-006 深化: 岗位可在多部门挂靠)
CREATE TABLE org_position_dept (
  id            BIGINT       NOT NULL,                            -- 主键: 雪花ID
  tenant_id     BIGINT       NOT NULL,                            -- 租户ID
  position_id   BIGINT       NOT NULL,                            -- 岗位ID(逻辑外键 org_position.id)
  dept_id       BIGINT       NOT NULL,                            -- 部门ID(逻辑外键 org_dept.id)
  created_by    BIGINT       NOT NULL DEFAULT 0,                  -- 创建人ID,0=系统
  updated_by    BIGINT       NOT NULL DEFAULT 0,                  -- 更新人ID
  created_at    TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,  -- 创建时间(UTC)
  updated_at    TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,  -- 更新时间(UTC,应用层维护)
  deleted       SMALLINT     NOT NULL DEFAULT 0,                  -- 逻辑删除 0=否 1=是
  version       INTEGER      NOT NULL DEFAULT 0,                  -- 乐观锁版本号
  CONSTRAINT pk_org_position_dept PRIMARY KEY (id),
  CONSTRAINT uk_org_position_dept UNIQUE (tenant_id, position_id, dept_id)
);
CREATE INDEX idx_org_position_dept_dept ON org_position_dept (tenant_id, dept_id);

-- 职务/职级序列
CREATE TABLE org_job (
  id            BIGINT       NOT NULL,                            -- 主键: 雪花ID
  tenant_id     BIGINT       NOT NULL,                            -- 租户ID
  name          VARCHAR(64)  NOT NULL,                            -- 职务名称
  code          VARCHAR(64)  NOT NULL,                            -- 职务编码
  status        SMALLINT     NOT NULL DEFAULT 1,                  -- 状态 1=有效 0=失效
  created_by    BIGINT       NOT NULL DEFAULT 0,                  -- 创建人ID,0=系统
  updated_by    BIGINT       NOT NULL DEFAULT 0,                  -- 更新人ID
  created_at    TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,  -- 创建时间(UTC)
  updated_at    TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,  -- 更新时间(UTC,应用层维护)
  deleted       SMALLINT     NOT NULL DEFAULT 0,                  -- 逻辑删除 0=否 1=是
  version       INTEGER      NOT NULL DEFAULT 0,                  -- 乐观锁版本号
  CONSTRAINT pk_org_job PRIMARY KEY (id),
  CONSTRAINT uk_org_job_code UNIQUE (tenant_id, code)
);

-- 职级
CREATE TABLE org_grade (
  id            BIGINT       NOT NULL,                            -- 主键: 雪花ID
  tenant_id     BIGINT       NOT NULL,                            -- 租户ID
  name          VARCHAR(64)  NOT NULL,                            -- 职级名称
  code          VARCHAR(64)  NOT NULL,                            -- 职级编码
  grade_band_id BIGINT,                                           -- 所属职级带ID(逻辑外键 org_grade_band.id)
  status        SMALLINT     NOT NULL DEFAULT 1,                  -- 状态 1=有效 0=失效
  created_by    BIGINT       NOT NULL DEFAULT 0,                  -- 创建人ID,0=系统
  updated_by    BIGINT       NOT NULL DEFAULT 0,                  -- 更新人ID
  created_at    TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,  -- 创建时间(UTC)
  updated_at    TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,  -- 更新时间(UTC,应用层维护)
  deleted       SMALLINT     NOT NULL DEFAULT 0,                  -- 逻辑删除 0=否 1=是
  version       INTEGER      NOT NULL DEFAULT 0,                  -- 乐观锁版本号
  CONSTRAINT pk_org_grade PRIMARY KEY (id),
  CONSTRAINT uk_org_grade_code UNIQUE (tenant_id, code)
);

-- 职级带(广播表)
CREATE TABLE org_grade_band (
  id            BIGINT       NOT NULL,                            -- 主键: 雪花ID
  tenant_id     BIGINT       NOT NULL,                            -- 租户ID
  name          VARCHAR(64)  NOT NULL,                            -- 职级带名称
  code          VARCHAR(64)  NOT NULL,                            -- 职级带编码
  min_grade     VARCHAR(16),                                      -- 带内最低职级编码
  max_grade     VARCHAR(16),                                      -- 带内最高职级编码
  created_by    BIGINT       NOT NULL DEFAULT 0,                  -- 创建人ID,0=系统
  updated_by    BIGINT       NOT NULL DEFAULT 0,                  -- 更新人ID
  created_at    TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,  -- 创建时间(UTC)
  updated_at    TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,  -- 更新时间(UTC,应用层维护)
  deleted       SMALLINT     NOT NULL DEFAULT 0,                  -- 逻辑删除 0=否 1=是
  version       INTEGER      NOT NULL DEFAULT 0,                  -- 乐观锁版本号
  CONSTRAINT pk_org_grade_band PRIMARY KEY (id),
  CONSTRAINT uk_org_grade_band_code UNIQUE (tenant_id, code)
);

-- 编制
CREATE TABLE org_headcount (
  id            BIGINT       NOT NULL,                            -- 主键: 雪花ID
  tenant_id     BIGINT       NOT NULL,                            -- 租户ID
  dept_id       BIGINT       NOT NULL,                            -- 部门ID(逻辑外键 org_dept.id)
  position_id   BIGINT       NOT NULL,                            -- 岗位ID(逻辑外键 org_position.id)
  period        VARCHAR(16)  NOT NULL,                            -- 编制周期(如 2026Q1)
  quota         INTEGER      NOT NULL DEFAULT 0,                  -- 编制数
  occupied      INTEGER      NOT NULL DEFAULT 0,                  -- 已占用数
  created_by    BIGINT       NOT NULL DEFAULT 0,                  -- 创建人ID,0=系统
  updated_by    BIGINT       NOT NULL DEFAULT 0,                  -- 更新人ID
  created_at    TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,  -- 创建时间(UTC)
  updated_at    TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,  -- 更新时间(UTC,应用层维护)
  deleted       SMALLINT     NOT NULL DEFAULT 0,                  -- 逻辑删除 0=否 1=是
  version       INTEGER      NOT NULL DEFAULT 0,                  -- 乐观锁版本号
  CONSTRAINT pk_org_headcount PRIMARY KEY (id),
  CONSTRAINT uk_org_headcount UNIQUE (tenant_id, dept_id, position_id, period)
);

-- 编制占用流水
CREATE TABLE org_hc_occupancy (
  id             BIGINT       NOT NULL,                           -- 主键: 雪花ID
  tenant_id      BIGINT       NOT NULL,                           -- 租户ID
  emp_id         BIGINT       NOT NULL,                           -- 员工ID(逻辑外键 emp_employee.id)
  requisition_id BIGINT,                                          -- 招聘需求ID(逻辑外键 rcu_requisition.id)
  change_type    VARCHAR(16)  NOT NULL,                           -- 变动类型 OCCUPY/RELEASE/TRANSFER
  dept_id        BIGINT       NOT NULL,                           -- 部门ID(逻辑外键 org_dept.id)
  position_id    BIGINT       NOT NULL,                           -- 岗位ID(逻辑外键 org_position.id)
  trans_no       VARCHAR(64),                                     -- 事务幂等键(回写来源 emp_pending_transaction.trans_no, §10.5-B8)
  created_by     BIGINT       NOT NULL DEFAULT 0,                 -- 创建人ID,0=系统
  updated_by     BIGINT       NOT NULL DEFAULT 0,                 -- 更新人ID
  created_at     TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP, -- 创建时间(UTC)
  updated_at     TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP, -- 更新时间(UTC,应用层维护)
  deleted        SMALLINT     NOT NULL DEFAULT 0,                 -- 逻辑删除 0=否 1=是
  version        INTEGER      NOT NULL DEFAULT 0,                 -- 乐观锁版本号
  CONSTRAINT pk_org_hc_occupancy PRIMARY KEY (id)
);
CREATE INDEX idx_org_hc_occupancy_emp ON org_hc_occupancy (tenant_id, emp_id);
CREATE INDEX idx_org_hc_occupancy_trans ON org_hc_occupancy (tenant_id, trans_no);

-- 部门历史快照
CREATE TABLE org_dept_history (
  id            BIGINT       NOT NULL,                            -- 主键: 雪花ID
  tenant_id     BIGINT       NOT NULL,                            -- 租户ID
  dept_id       BIGINT       NOT NULL,                            -- 部门ID(逻辑外键 org_dept.id)
  snapshot_date DATE         NOT NULL,                            -- 快照日期
  snapshot      JSONB        NOT NULL,                            -- 部门快照(树/属性/负责人)
  created_by    BIGINT       NOT NULL DEFAULT 0,                  -- 创建人ID,0=系统
  updated_by    BIGINT       NOT NULL DEFAULT 0,                  -- 更新人ID
  created_at    TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,  -- 创建时间(UTC)
  updated_at    TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,  -- 更新时间(UTC,应用层维护)
  deleted       SMALLINT     NOT NULL DEFAULT 0,                  -- 逻辑删除 0=否 1=是
  version       INTEGER      NOT NULL DEFAULT 0,                  -- 乐观锁版本号
  CONSTRAINT pk_org_dept_history PRIMARY KEY (id)
);
CREATE INDEX idx_org_dept_history ON org_dept_history (tenant_id, dept_id, snapshot_date);

-- 组织调整申请单快照(独立表, 根治 org_dept_history 快照污染 §10.5-B7)
CREATE TABLE org_adjustment_snapshot (
  id            BIGINT       NOT NULL,                            -- 主键: 雪花ID
  tenant_id     BIGINT       NOT NULL,                            -- 租户ID
  adjustment_id BIGINT       NOT NULL,                            -- 调整申请ID(雪花ID, 对应流程业务键 bizKey=ORG_ADJUST:{adjustment_id})
  dept_id       BIGINT       NOT NULL,                            -- 部门ID(逻辑外键 org_dept.id, CREATE 类申请为 0)
  snapshot_json JSONB        NOT NULL,                            -- 调整申请单 JSON(kind=ORG_ADJUST_REQUEST, 含状态/载荷/审批意见)
  created_by    BIGINT       NOT NULL DEFAULT 0,                  -- 创建人ID,0=系统
  updated_by    BIGINT       NOT NULL DEFAULT 0,                  -- 更新人ID
  created_at    TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,  -- 创建时间(UTC)
  updated_at    TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,  -- 更新时间(UTC,应用层维护)
  deleted       SMALLINT     NOT NULL DEFAULT 0,                  -- 逻辑删除 0=否 1=是
  version       INTEGER      NOT NULL DEFAULT 0,                  -- 乐观锁版本号
  CONSTRAINT pk_org_adjustment_snapshot PRIMARY KEY (id),
  CONSTRAINT uk_org_adjustment_snapshot UNIQUE (tenant_id, adjustment_id, dept_id)
);

-- 组织版本-岗位快照(批8#16 MVP: 组织版本扩展到岗位)
CREATE TABLE org_position_version (
  id            BIGINT       NOT NULL,                            -- 主键: 雪花ID
  tenant_id     BIGINT       NOT NULL,                            -- 租户ID
  version_no    VARCHAR(64)  NOT NULL,                            -- 组织版本号(同批次快照共享, 如 V+雪花ID)
  position_id   BIGINT       NOT NULL,                            -- 岗位ID(逻辑外键 org_position.id)
  snapshot_date DATE         NOT NULL,                            -- 快照日期
  snapshot      JSONB        NOT NULL,                            -- 岗位快照(主数据+挂靠部门, 不含员工归属)
  created_by    BIGINT       NOT NULL DEFAULT 0,                  -- 创建人ID,0=系统
  updated_by    BIGINT       NOT NULL DEFAULT 0,                  -- 更新人ID
  created_at    TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,  -- 创建时间(UTC)
  updated_at    TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,  -- 更新时间(UTC,应用层维护)
  deleted       SMALLINT     NOT NULL DEFAULT 0,                  -- 逻辑删除 0=否 1=是
  version       INTEGER      NOT NULL DEFAULT 0,                  -- 乐观锁版本号
  CONSTRAINT pk_org_position_version PRIMARY KEY (id),
  CONSTRAINT uk_org_position_version UNIQUE (tenant_id, version_no, position_id)
);
CREATE INDEX idx_org_position_version_date ON org_position_version (tenant_id, position_id, snapshot_date);

-- 组织版本-职级快照(批8#16 MVP: 组织版本扩展到职级)
CREATE TABLE org_grade_version (
  id            BIGINT       NOT NULL,                            -- 主键: 雪花ID
  tenant_id     BIGINT       NOT NULL,                            -- 租户ID
  version_no    VARCHAR(64)  NOT NULL,                            -- 组织版本号(同批次快照共享, 如 V+雪花ID)
  grade_id      BIGINT       NOT NULL,                            -- 职级ID(逻辑外键 org_grade.id)
  snapshot_date DATE         NOT NULL,                            -- 快照日期
  snapshot      JSONB        NOT NULL,                            -- 职级快照(主数据)
  created_by    BIGINT       NOT NULL DEFAULT 0,                  -- 创建人ID,0=系统
  updated_by    BIGINT       NOT NULL DEFAULT 0,                  -- 更新人ID
  created_at    TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,  -- 创建时间(UTC)
  updated_at    TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,  -- 更新时间(UTC,应用层维护)
  deleted       SMALLINT     NOT NULL DEFAULT 0,                  -- 逻辑删除 0=否 1=是
  version       INTEGER      NOT NULL DEFAULT 0,                  -- 乐观锁版本号
  CONSTRAINT pk_org_grade_version PRIMARY KEY (id),
  CONSTRAINT uk_org_grade_version UNIQUE (tenant_id, version_no, grade_id)
);
CREATE INDEX idx_org_grade_version_date ON org_grade_version (tenant_id, grade_id, snapshot_date);

-- =====================================================================
-- 域 3: emp_db 员工 (全表按 tenant_id 分片, 绑定表组)
-- =====================================================================
CREATE SCHEMA IF NOT EXISTS emp_db;
SET search_path TO emp_db;

-- 员工主档
CREATE TABLE emp_employee (
  id               BIGINT       NOT NULL,                         -- 主键: 雪花ID
  tenant_id        BIGINT       NOT NULL,                         -- 租户ID
  emp_no           VARCHAR(32)  NOT NULL,                         -- 员工工号
  name             VARCHAR(64)  NOT NULL,                         -- 姓名
  pinyin_sort_key  VARCHAR(128),                                  -- 姓名拼音排序键(应用生成,规避排序规则差异)
  cert_type        VARCHAR(16)  NOT NULL DEFAULT 'ID_CARD',       -- 证件类型
  cert_no_cipher   VARCHAR(256),                                  -- 证件号密文(AES-256-GCM,逻辑列 cert_no)
  cert_no_hash     CHAR(64),                                      -- 证件号哈希 SHA-256(cert_no)小写十六进制,等值查重用(再入职识别,不随加密改造失效)
  cert_no_mask     VARCHAR(64),                                   -- 证件号掩码(保留首尾)
  mobile_cipher    VARCHAR(256),                                  -- 手机号密文(逻辑列 mobile)
  mobile_mask      VARCHAR(32),                                   -- 手机号掩码
  email            VARCHAR(128),                                  -- 邮箱
  gender           VARCHAR(8),                                    -- 性别 M/F
  birth_date       DATE,                                          -- 出生日期
  status           SMALLINT     NOT NULL DEFAULT 0,               -- 员工状态机(8态,见03-02§4) 0=待入职 1=试用 2=在职 3=离职中 4=已离职 5=已取消 6=退休 7=黑名单
  blacklisted      SMALLINT     NOT NULL DEFAULT 0,               -- 是否黑名单 1=是 0=否
  created_by       BIGINT       NOT NULL DEFAULT 0,               -- 创建人ID,0=系统
  updated_by       BIGINT       NOT NULL DEFAULT 0,               -- 更新人ID
  created_at       TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP, -- 创建时间(UTC)
  updated_at       TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP, -- 更新时间(UTC,应用层维护)
  deleted          SMALLINT     NOT NULL DEFAULT 0,               -- 逻辑删除 0=否 1=是
  version          INTEGER      NOT NULL DEFAULT 0,               -- 乐观锁版本号
  CONSTRAINT pk_emp_employee PRIMARY KEY (id),
  CONSTRAINT uk_emp_employee_no UNIQUE (tenant_id, emp_no)
);
CREATE INDEX idx_emp_employee_status ON emp_employee (tenant_id, status);
CREATE INDEX idx_emp_employee_pinyin ON emp_employee (tenant_id, pinyin_sort_key);
CREATE INDEX idx_emp_cert_hash ON emp_employee (tenant_id, cert_no_hash);

-- 雇佣关系版本链(核心)
CREATE TABLE emp_employment (
  id               BIGINT       NOT NULL,                         -- 主键: 雪花ID
  tenant_id        BIGINT       NOT NULL,                         -- 租户ID
  emp_id           BIGINT       NOT NULL,                         -- 员工ID(逻辑外键 emp_employee.id)
  legal_entity_id  BIGINT       NOT NULL,                         -- 法人ID(逻辑外键 org_legal_entity.id)
  dept_id          BIGINT       NOT NULL,                         -- 部门ID(逻辑外键 org_dept.id)
  position_id      BIGINT       NOT NULL,                         -- 岗位ID(逻辑外键 org_position.id)
  grade_id         BIGINT,                                        -- 职级ID(逻辑外键 org_grade.id)
  hire_type        VARCHAR(16)  NOT NULL,                         -- 雇佣类型 FULLTIME/PARTTIME/INTERN/CONSULTANT
  supervisor_emp_id BIGINT,                                       -- 直属上级员工ID(逻辑外键 emp_employee.id)
  effective_from   DATE         NOT NULL,                         -- 生效开始日
  effective_to     DATE,                                          -- 生效结束日(NULL=现行)
  status           SMALLINT     NOT NULL DEFAULT 1,               -- 状态 1=有效 0=失效
  created_by       BIGINT       NOT NULL DEFAULT 0,               -- 创建人ID,0=系统
  updated_by       BIGINT       NOT NULL DEFAULT 0,               -- 更新人ID
  created_at       TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP, -- 创建时间(UTC)
  updated_at       TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP, -- 更新时间(UTC,应用层维护)
  deleted          SMALLINT     NOT NULL DEFAULT 0,               -- 逻辑删除 0=否 1=是
  version          INTEGER      NOT NULL DEFAULT 0,               -- 乐观锁版本号
  CONSTRAINT pk_emp_employment PRIMARY KEY (id),
  CONSTRAINT uk_emp_employment_eff UNIQUE (tenant_id, emp_id, effective_from)
);
CREATE INDEX idx_emp_employment_dept ON emp_employment (tenant_id, dept_id);
CREATE INDEX idx_emp_employment_position ON emp_employment (tenant_id, position_id);

-- 教育经历
CREATE TABLE emp_profile_education (
  id            BIGINT       NOT NULL,                            -- 主键: 雪花ID
  tenant_id     BIGINT       NOT NULL,                            -- 租户ID
  employee_id   BIGINT       NOT NULL,                            -- 员工ID(逻辑外键 emp_employee.id)
  school        VARCHAR(128) NOT NULL,                            -- 学校名称
  major         VARCHAR(128),                                     -- 专业
  degree        VARCHAR(16),                                      -- 学历
  start_date    DATE,                                             -- 开始日期
  end_date      DATE,                                             -- 结束日期
  created_by    BIGINT       NOT NULL DEFAULT 0,                  -- 创建人ID,0=系统
  updated_by    BIGINT       NOT NULL DEFAULT 0,                  -- 更新人ID
  created_at    TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,  -- 创建时间(UTC)
  updated_at    TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,  -- 更新时间(UTC,应用层维护)
  deleted       SMALLINT     NOT NULL DEFAULT 0,                  -- 逻辑删除 0=否 1=是
  version       INTEGER      NOT NULL DEFAULT 0,                  -- 乐观锁版本号
  CONSTRAINT pk_emp_profile_education PRIMARY KEY (id)
);
CREATE INDEX idx_emp_edu_employee ON emp_profile_education (tenant_id, employee_id);

-- 工作经历
CREATE TABLE emp_profile_work (
  id            BIGINT       NOT NULL,                            -- 主键: 雪花ID
  tenant_id     BIGINT       NOT NULL,                            -- 租户ID
  employee_id   BIGINT       NOT NULL,                            -- 员工ID(逻辑外键 emp_employee.id)
  company       VARCHAR(128) NOT NULL,                            -- 公司名称
  position_name VARCHAR(64),                                      -- 职位名称
  start_date    DATE,                                             -- 开始日期
  end_date      DATE,                                             -- 结束日期
  description   VARCHAR(512),                                     -- 工作描述
  created_by    BIGINT       NOT NULL DEFAULT 0,                  -- 创建人ID,0=系统
  updated_by    BIGINT       NOT NULL DEFAULT 0,                  -- 更新人ID
  created_at    TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,  -- 创建时间(UTC)
  updated_at    TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,  -- 更新时间(UTC,应用层维护)
  deleted       SMALLINT     NOT NULL DEFAULT 0,                  -- 逻辑删除 0=否 1=是
  version       INTEGER      NOT NULL DEFAULT 0,                  -- 乐观锁版本号
  CONSTRAINT pk_emp_profile_work PRIMARY KEY (id)
);
CREATE INDEX idx_emp_work_employee ON emp_profile_work (tenant_id, employee_id);

-- 家庭成员
CREATE TABLE emp_profile_family (
  id            BIGINT       NOT NULL,                            -- 主键: 雪花ID
  tenant_id     BIGINT       NOT NULL,                            -- 租户ID
  employee_id   BIGINT       NOT NULL,                            -- 员工ID(逻辑外键 emp_employee.id)
  name          VARCHAR(64)  NOT NULL,                            -- 成员姓名
  relation      VARCHAR(16)  NOT NULL,                            -- 关系(配偶/父母/子女)
  phone         VARCHAR(32),                                      -- 联系电话
  company       VARCHAR(128),                                     -- 工作单位
  created_by    BIGINT       NOT NULL DEFAULT 0,                  -- 创建人ID,0=系统
  updated_by    BIGINT       NOT NULL DEFAULT 0,                  -- 更新人ID
  created_at    TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,  -- 创建时间(UTC)
  updated_at    TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,  -- 更新时间(UTC,应用层维护)
  deleted       SMALLINT     NOT NULL DEFAULT 0,                  -- 逻辑删除 0=否 1=是
  version       INTEGER      NOT NULL DEFAULT 0,                  -- 乐观锁版本号
  CONSTRAINT pk_emp_profile_family PRIMARY KEY (id)
);
CREATE INDEX idx_emp_family_employee ON emp_profile_family (tenant_id, employee_id);

-- 紧急联系人
CREATE TABLE emp_profile_emergency (
  id            BIGINT       NOT NULL,                            -- 主键: 雪花ID
  tenant_id     BIGINT       NOT NULL,                            -- 租户ID
  employee_id   BIGINT       NOT NULL,                            -- 员工ID(逻辑外键 emp_employee.id)
  name          VARCHAR(64)  NOT NULL,                            -- 联系人姓名
  relation      VARCHAR(16)  NOT NULL,                            -- 关系
  phone         VARCHAR(32)  NOT NULL,                            -- 联系电话
  address       VARCHAR(255),                                     -- 联系地址
  created_by    BIGINT       NOT NULL DEFAULT 0,                  -- 创建人ID,0=系统
  updated_by    BIGINT       NOT NULL DEFAULT 0,                  -- 更新人ID
  created_at    TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,  -- 创建时间(UTC)
  updated_at    TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,  -- 更新时间(UTC,应用层维护)
  deleted       SMALLINT     NOT NULL DEFAULT 0,                  -- 逻辑删除 0=否 1=是
  version       INTEGER      NOT NULL DEFAULT 0,                  -- 乐观锁版本号
  CONSTRAINT pk_emp_profile_emergency PRIMARY KEY (id)
);
CREATE INDEX idx_emp_emergency_employee ON emp_profile_emergency (tenant_id, employee_id);

-- 合同台账
CREATE TABLE emp_contract (
  id               BIGINT       NOT NULL,                         -- 主键: 雪花ID
  tenant_id        BIGINT       NOT NULL,                         -- 租户ID
  emp_id           BIGINT       NOT NULL,                         -- 员工ID(逻辑外键 emp_employee.id)
  legal_entity_id  BIGINT,                                        -- 签订法人ID(逻辑外键 org_legal_entity.id, NULL=未指定)
  contract_no      VARCHAR(64)  NOT NULL,                         -- 合同编号
  type             VARCHAR(16)  NOT NULL,                         -- 合同类型 FIXED/OPEN_ENDED/LABOR/INTERN
  start_date       DATE         NOT NULL,                         -- 合同开始日
  end_date         DATE,                                          -- 合同结束日(无固定期限为NULL)
  probation_end    DATE,                                          -- 试用期结束日
  renew_cnt        INTEGER      NOT NULL DEFAULT 0,               -- 续签次数
  file_id          BIGINT,                                        -- 合同附件ID(逻辑外键 sys_file_meta.id)
  status           SMALLINT     NOT NULL DEFAULT 1,               -- 状态 1=有效 0=终止
  created_by       BIGINT       NOT NULL DEFAULT 0,               -- 创建人ID,0=系统
  updated_by       BIGINT       NOT NULL DEFAULT 0,               -- 更新人ID
  created_at       TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP, -- 创建时间(UTC)
  updated_at       TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP, -- 更新时间(UTC,应用层维护)
  deleted          SMALLINT     NOT NULL DEFAULT 0,               -- 逻辑删除 0=否 1=是
  version          INTEGER      NOT NULL DEFAULT 0,               -- 乐观锁版本号
  CONSTRAINT pk_emp_contract PRIMARY KEY (id),
  CONSTRAINT uk_emp_contract_no UNIQUE (tenant_id, contract_no)
);
CREATE INDEX idx_emp_contract_emp ON emp_contract (tenant_id, emp_id);

-- 合同条款明细(EMP-F-014 深化: 试用/薪酬/保密/竞业/自定义条款)
CREATE TABLE emp_contract_term (
  id             BIGINT        NOT NULL,                        -- 主键: 雪花ID
  tenant_id      BIGINT        NOT NULL,                        -- 租户ID
  contract_id    BIGINT        NOT NULL,                        -- 合同ID(逻辑外键 emp_contract.id)
  term_type      VARCHAR(32)   NOT NULL,                        -- 条款类型 PROBATION/SALARY/CONFIDENTIALITY/NON_COMPETE/CUSTOM
  content        VARCHAR(1000) NOT NULL,                        -- 条款内容
  effective_from DATE          NOT NULL,                        -- 生效日
  effective_to   DATE,                                          -- 失效日(NULL=长期有效)
  created_by     BIGINT        NOT NULL DEFAULT 0,              -- 创建人ID,0=系统
  updated_by     BIGINT        NOT NULL DEFAULT 0,              -- 更新人ID
  created_at     TIMESTAMP(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP, -- 创建时间(UTC)
  updated_at     TIMESTAMP(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP, -- 更新时间(UTC,应用层维护)
  deleted        SMALLINT      NOT NULL DEFAULT 0,              -- 逻辑删除 0=否 1=是
  version        INTEGER       NOT NULL DEFAULT 0,              -- 乐观锁版本号
  CONSTRAINT pk_emp_contract_term PRIMARY KEY (id)
);
CREATE INDEX idx_emp_contract_term ON emp_contract_term (tenant_id, contract_id);

-- 生命周期单(入转调离)
CREATE TABLE emp_lifecycle_case (
  id            BIGINT       NOT NULL,                            -- 主键: 雪花ID
  tenant_id     BIGINT       NOT NULL,                            -- 租户ID
  emp_id        BIGINT       NOT NULL,                            -- 员工ID(逻辑外键 emp_employee.id)
  type          VARCHAR(16)  NOT NULL,                            -- 类型 ONBOARD/REGULAR/TRANSFER/RESIGN
  biz_key       VARCHAR(64)  NOT NULL,                            -- 业务单号(流程幂等键)
  payload       JSONB,                                            -- 单据业务数据
  proc_inst_id  VARCHAR(64),                                      -- 流程实例ID(逻辑外键 flow_ext_biz_relation.proc_inst_id)
  state         VARCHAR(16)  NOT NULL DEFAULT 'DRAFT',            -- 状态 DRAFT/PROCESSING/DONE/CANCELLED
  effect_date   DATE,                                             -- 生效日期
  created_by    BIGINT       NOT NULL DEFAULT 0,                  -- 创建人ID,0=系统
  updated_by    BIGINT       NOT NULL DEFAULT 0,                  -- 更新人ID
  created_at    TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,  -- 创建时间(UTC)
  updated_at    TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,  -- 更新时间(UTC,应用层维护)
  deleted       SMALLINT     NOT NULL DEFAULT 0,                  -- 逻辑删除 0=否 1=是
  version       INTEGER      NOT NULL DEFAULT 0,                  -- 乐观锁版本号
  CONSTRAINT pk_emp_lifecycle_case PRIMARY KEY (id),
  CONSTRAINT uk_emp_lifecycle_biz UNIQUE (tenant_id, biz_key)
);
CREATE INDEX idx_emp_lifecycle_emp ON emp_lifecycle_case (tenant_id, emp_id, type);

-- 待生效事务队列(幂等落账)
CREATE TABLE emp_pending_transaction (
  id            BIGINT       NOT NULL,                            -- 主键: 雪花ID
  tenant_id     BIGINT       NOT NULL,                            -- 租户ID
  emp_id        BIGINT       NOT NULL,                            -- 员工ID(逻辑外键 emp_employee.id)
  trans_no      VARCHAR(64)  NOT NULL,                            -- 事务流水号(幂等键)
  trans_type    VARCHAR(16)  NOT NULL,                            -- 事务类型 ONBOARD/REGULAR/TRANSFER/RESIGN/PROMOTE
  effect_date   DATE         NOT NULL,                            -- 生效日期
  payload       JSONB,                                            -- 事务载荷
  state         VARCHAR(16)  NOT NULL DEFAULT 'PENDING',          -- 状态 PENDING/APPLIED/CANCELLED
  created_by    BIGINT       NOT NULL DEFAULT 0,                  -- 创建人ID,0=系统
  updated_by    BIGINT       NOT NULL DEFAULT 0,                  -- 更新人ID
  created_at    TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,  -- 创建时间(UTC)
  updated_at    TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,  -- 更新时间(UTC,应用层维护)
  deleted       SMALLINT     NOT NULL DEFAULT 0,                  -- 逻辑删除 0=否 1=是
  version       INTEGER      NOT NULL DEFAULT 0,                  -- 乐观锁版本号
  CONSTRAINT pk_emp_pending_transaction PRIMARY KEY (id),
  CONSTRAINT uk_emp_pending_trans_no UNIQUE (tenant_id, trans_no)
);
CREATE INDEX idx_emp_pending_effect ON emp_pending_transaction (tenant_id, effect_date);

-- 员工状态流水
CREATE TABLE emp_status_log (
  id            BIGINT       NOT NULL,                            -- 主键: 雪花ID
  tenant_id     BIGINT       NOT NULL,                            -- 租户ID
  emp_id        BIGINT       NOT NULL,                            -- 员工ID(逻辑外键 emp_employee.id)
  from_status   SMALLINT,                                         -- 变更前状态
  to_status     SMALLINT     NOT NULL,                            -- 变更后状态
  reason        VARCHAR(255),                                     -- 变更原因
  created_by    BIGINT       NOT NULL DEFAULT 0,                  -- 创建人ID,0=系统
  updated_by    BIGINT       NOT NULL DEFAULT 0,                  -- 更新人ID
  created_at    TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,  -- 创建时间(UTC)
  updated_at    TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,  -- 更新时间(UTC,应用层维护)
  deleted       SMALLINT     NOT NULL DEFAULT 0,                  -- 逻辑删除 0=否 1=是
  version       INTEGER      NOT NULL DEFAULT 0,                  -- 乐观锁版本号
  CONSTRAINT pk_emp_status_log PRIMARY KEY (id)
);
CREATE INDEX idx_emp_status_log_emp ON emp_status_log (tenant_id, emp_id, created_at);

-- 字段变更审计
CREATE TABLE emp_change_audit (
  id            BIGINT       NOT NULL,                            -- 主键: 雪花ID
  tenant_id     BIGINT       NOT NULL,                            -- 租户ID
  emp_id        BIGINT       NOT NULL,                            -- 员工ID(逻辑外键 emp_employee.id)
  field_name    VARCHAR(64)  NOT NULL,                            -- 变更字段
  old_value     VARCHAR(512),                                     -- 旧值
  new_value     VARCHAR(512),                                     -- 新值
  created_by    BIGINT       NOT NULL DEFAULT 0,                  -- 创建人ID(操作人)
  updated_by    BIGINT       NOT NULL DEFAULT 0,                  -- 更新人ID
  created_at    TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,  -- 创建时间(UTC)
  updated_at    TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,  -- 更新时间(UTC,应用层维护)
  deleted       SMALLINT     NOT NULL DEFAULT 0,                  -- 逻辑删除 0=否 1=是
  version       INTEGER      NOT NULL DEFAULT 0,                  -- 乐观锁版本号
  CONSTRAINT pk_emp_change_audit PRIMARY KEY (id)
);
CREATE INDEX idx_emp_change_audit ON emp_change_audit (tenant_id, emp_id, field_name);

-- =====================================================================
-- 域 4: att_db 假勤 (att_punch_record 分片 + 按月分区)
-- =====================================================================
CREATE SCHEMA IF NOT EXISTS att_db;
SET search_path TO att_db;

-- 打卡记录(分片+月分区; 月度分区由平台调度任务维护, 此处给示例分区)
CREATE TABLE att_punch_record (
  id           BIGINT       NOT NULL,                             -- 主键: 雪花ID
  tenant_id    BIGINT       NOT NULL,                             -- 租户ID
  emp_id       BIGINT       NOT NULL,                             -- 员工ID(逻辑外键 emp_employee.id)
  punch_time   TIMESTAMP(3) NOT NULL,                             -- 打卡时间(UTC,分区键)
  type         VARCHAR(8)   NOT NULL,                             -- 打卡类型 IN/OUT
  source       VARCHAR(16)  NOT NULL DEFAULT 'APP',               -- 打卡来源 APP/DEVICE/GPS
  location     VARCHAR(255),                                      -- 打卡地点
  device_id    VARCHAR(64),                                       -- 设备标识
  att_group_id BIGINT,                                            -- 班组ID(逻辑外键 att_group.id)
  status       SMALLINT     NOT NULL DEFAULT 0,                   -- 状态 0=正常 1=异常
  created_by   BIGINT       NOT NULL DEFAULT 0,                   -- 创建人ID,0=系统
  updated_by   BIGINT       NOT NULL DEFAULT 0,                   -- 更新人ID
  created_at   TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,   -- 创建时间(UTC)
  updated_at   TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,   -- 更新时间(UTC,应用层维护)
  deleted      SMALLINT     NOT NULL DEFAULT 0,                   -- 逻辑删除 0=否 1=是
  version      INTEGER      NOT NULL DEFAULT 0,                   -- 乐观锁版本号
  CONSTRAINT pk_att_punch_record PRIMARY KEY (id, punch_time),
  CONSTRAINT uk_att_punch_emp_time UNIQUE (tenant_id, emp_id, punch_time)
) PARTITION BY RANGE (punch_time);

CREATE TABLE att_punch_record_202601 PARTITION OF att_punch_record FOR VALUES FROM ('2026-01-01') TO ('2026-02-01');
CREATE TABLE att_punch_record_202602 PARTITION OF att_punch_record FOR VALUES FROM ('2026-02-01') TO ('2026-03-01');
CREATE TABLE att_punch_record_202603 PARTITION OF att_punch_record FOR VALUES FROM ('2026-03-01') TO ('2026-04-01');

-- 班组
CREATE TABLE att_group (
  id            BIGINT       NOT NULL,                            -- 主键: 雪花ID
  tenant_id     BIGINT       NOT NULL,                            -- 租户ID
  name          VARCHAR(64)  NOT NULL,                            -- 班组名称
  code          VARCHAR(64)  NOT NULL,                            -- 班组编码
  leader_emp_id BIGINT,                                           -- 班组长员工ID(逻辑外键 emp_employee.id)
  holiday_calendar_year INTEGER,                                 -- 绑定的节假日历年度(空=不绑定,日考勤按此年取att_holiday)
  created_by    BIGINT       NOT NULL DEFAULT 0,                  -- 创建人ID,0=系统
  updated_by    BIGINT       NOT NULL DEFAULT 0,                  -- 更新人ID
  created_at    TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,  -- 创建时间(UTC)
  updated_at    TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,  -- 更新时间(UTC,应用层维护)
  deleted       SMALLINT     NOT NULL DEFAULT 0,                  -- 逻辑删除 0=否 1=是
  version       INTEGER      NOT NULL DEFAULT 0,                  -- 乐观锁版本号
  CONSTRAINT pk_att_group PRIMARY KEY (id),
  CONSTRAINT uk_att_group_code UNIQUE (tenant_id, code)
);

-- 考勤组成员(员工与班组归属, 批6#10; 全量覆盖语义维护, 物理删除避免逻辑删残留占唯一键)
CREATE TABLE att_group_member (
  id            BIGINT       NOT NULL,                            -- 主键: 雪花ID
  tenant_id     BIGINT       NOT NULL,                            -- 租户ID
  group_id      BIGINT       NOT NULL,                            -- 班组ID(逻辑外键 att_group.id)
  emp_id        BIGINT       NOT NULL,                            -- 员工ID(逻辑外键 emp_employee.id)
  created_by    BIGINT       NOT NULL DEFAULT 0,                  -- 创建人ID,0=系统
  updated_by    BIGINT       NOT NULL DEFAULT 0,                  -- 更新人ID
  created_at    TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,  -- 创建时间(UTC)
  updated_at    TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,  -- 更新时间(UTC,应用层维护)
  deleted       SMALLINT     NOT NULL DEFAULT 0,                  -- 逻辑删除 0=否 1=是
  version       INTEGER      NOT NULL DEFAULT 0,                  -- 乐观锁版本号
  CONSTRAINT pk_att_group_member PRIMARY KEY (id),
  CONSTRAINT uk_att_group_member UNIQUE (tenant_id, group_id, emp_id)
);
CREATE INDEX idx_att_group_member_emp ON att_group_member (tenant_id, emp_id);

-- 法定节假日与调班日
CREATE TABLE att_holiday (
  id            BIGINT       NOT NULL,                            -- 主键: 雪花ID
  tenant_id     BIGINT       NOT NULL,                            -- 租户ID
  calendar_year INTEGER      NOT NULL,                            -- 日历年度(=EXTRACT(YEAR FROM holiday_date),服务端派生)
  holiday_date  DATE         NOT NULL,                            -- 日期
  name          VARCHAR(64)  NOT NULL,                            -- 名称(如 春节/国庆调休)
  day_type      VARCHAR(16)  NOT NULL,                            -- 日期类型: HOLIDAY=法定节假日(放假) WORKDAY=调班补班(上班)
  remark        VARCHAR(255),                                     -- 备注
  created_by    BIGINT       NOT NULL DEFAULT 0,                  -- 创建人ID,0=系统
  updated_by    BIGINT       NOT NULL DEFAULT 0,                  -- 更新人ID
  created_at    TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,  -- 创建时间(UTC)
  updated_at    TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,  -- 更新时间(UTC,应用层维护)
  deleted       SMALLINT     NOT NULL DEFAULT 0,                  -- 逻辑删除 0=否 1=是
  version       INTEGER      NOT NULL DEFAULT 0,                  -- 乐观锁版本号
  CONSTRAINT pk_att_holiday PRIMARY KEY (id),
  CONSTRAINT uk_att_holiday_date UNIQUE (tenant_id, holiday_date)
);
CREATE INDEX idx_att_holiday_year ON att_holiday (tenant_id, calendar_year);
CREATE INDEX idx_att_holiday_type ON att_holiday (tenant_id, day_type);

-- 班次
CREATE TABLE att_shift (
  id            BIGINT       NOT NULL,                            -- 主键: 雪花ID
  tenant_id     BIGINT       NOT NULL,                            -- 租户ID
  name          VARCHAR(64)  NOT NULL,                            -- 班次名称
  code          VARCHAR(64)  NOT NULL,                            -- 班次编码
  start_time    TIME         NOT NULL,                            -- 上班时间
  end_time      TIME         NOT NULL,                            -- 下班时间
  flexible      SMALLINT     NOT NULL DEFAULT 0,                  -- 是否弹性 1=是 0=否
  cross_day     SMALLINT     NOT NULL DEFAULT 0,                  -- 是否跨天班次 1=是(下班时间为次日) 0=否
  created_by    BIGINT       NOT NULL DEFAULT 0,                  -- 创建人ID,0=系统
  updated_by    BIGINT       NOT NULL DEFAULT 0,                  -- 更新人ID
  created_at    TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,  -- 创建时间(UTC)
  updated_at    TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,  -- 更新时间(UTC,应用层维护)
  deleted       SMALLINT     NOT NULL DEFAULT 0,                  -- 逻辑删除 0=否 1=是
  version       INTEGER      NOT NULL DEFAULT 0,                  -- 乐观锁版本号
  CONSTRAINT pk_att_shift PRIMARY KEY (id),
  CONSTRAINT uk_att_shift_code UNIQUE (tenant_id, code)
);

-- 排班
CREATE TABLE att_shift_assignment (
  id            BIGINT       NOT NULL,                            -- 主键: 雪花ID
  tenant_id     BIGINT       NOT NULL,                            -- 租户ID
  emp_id        BIGINT       NOT NULL,                            -- 员工ID(逻辑外键 emp_employee.id)
  shift_id      BIGINT       NOT NULL,                            -- 班次ID(逻辑外键 att_shift.id)
  work_date     DATE         NOT NULL,                            -- 工作日
  created_by    BIGINT       NOT NULL DEFAULT 0,                  -- 创建人ID,0=系统
  updated_by    BIGINT       NOT NULL DEFAULT 0,                  -- 更新人ID
  created_at    TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,  -- 创建时间(UTC)
  updated_at    TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,  -- 更新时间(UTC,应用层维护)
  deleted       SMALLINT     NOT NULL DEFAULT 0,                  -- 逻辑删除 0=否 1=是
  version       INTEGER      NOT NULL DEFAULT 0,                  -- 乐观锁版本号
  CONSTRAINT pk_att_shift_assignment PRIMARY KEY (id),
  CONSTRAINT uk_att_assign_emp_date UNIQUE (tenant_id, emp_id, work_date)
);
CREATE INDEX idx_att_assign_shift ON att_shift_assignment (tenant_id, shift_id, work_date);

-- 日考勤结果
CREATE TABLE att_daily_attendance (
  id             BIGINT       NOT NULL,                           -- 主键: 雪花ID
  tenant_id      BIGINT       NOT NULL,                           -- 租户ID
  emp_id         BIGINT       NOT NULL,                           -- 员工ID(逻辑外键 emp_employee.id)
  work_date      DATE         NOT NULL,                           -- 工作日
  employment_id  BIGINT       NOT NULL,                           -- 雇佣关系ID(逻辑外键 emp_employment.id,日期归属版本)
  att_status     VARCHAR(16)  NOT NULL DEFAULT 'NORMAL',          -- 出勤状态 NORMAL/LATE/EARLY/ABSENT/LEAVE
  punch_in       TIMESTAMP(3),                                    -- 实际打卡上班时间
  punch_out      TIMESTAMP(3),                                    -- 实际打卡下班时间
  leave_hours    NUMERIC(6,2) NOT NULL DEFAULT 0,                 -- 请假时长(小时)
  overtime_hours NUMERIC(6,2) NOT NULL DEFAULT 0,                 -- 加班时长(小时)
  created_by     BIGINT       NOT NULL DEFAULT 0,                 -- 创建人ID,0=系统
  updated_by     BIGINT       NOT NULL DEFAULT 0,                 -- 更新人ID
  created_at     TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP, -- 创建时间(UTC)
  updated_at     TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP, -- 更新时间(UTC,应用层维护)
  deleted        SMALLINT     NOT NULL DEFAULT 0,                 -- 逻辑删除 0=否 1=是
  version        INTEGER      NOT NULL DEFAULT 0,                 -- 乐观锁版本号
  CONSTRAINT pk_att_daily_attendance PRIMARY KEY (id),
  CONSTRAINT uk_att_daily_emp_date UNIQUE (tenant_id, emp_id, work_date)
);

-- 考勤异常
CREATE TABLE att_exception (
  id             BIGINT       NOT NULL,                           -- 主键: 雪花ID
  tenant_id      BIGINT       NOT NULL,                           -- 租户ID
  emp_id         BIGINT       NOT NULL,                           -- 员工ID(逻辑外键 emp_employee.id)
  work_date      DATE         NOT NULL,                           -- 工作日
  exception_type VARCHAR(16)  NOT NULL,                           -- 异常类型 LATE/EARLY/MISSING_PUNCH/ABSENT
  status         VARCHAR(16)  NOT NULL DEFAULT 'OPEN',            -- 状态 OPEN/RESOLVED/IGNORED
  description    VARCHAR(255),                                    -- 异常说明
  created_by     BIGINT       NOT NULL DEFAULT 0,                 -- 创建人ID,0=系统
  updated_by     BIGINT       NOT NULL DEFAULT 0,                 -- 更新人ID
  created_at     TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP, -- 创建时间(UTC)
  updated_at     TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP, -- 更新时间(UTC,应用层维护)
  deleted        SMALLINT     NOT NULL DEFAULT 0,                 -- 逻辑删除 0=否 1=是
  version        INTEGER      NOT NULL DEFAULT 0,                 -- 乐观锁版本号
  CONSTRAINT pk_att_exception PRIMARY KEY (id)
);
CREATE INDEX idx_att_exception_emp ON att_exception (tenant_id, emp_id, work_date, status);

-- 假种(规则 json)
CREATE TABLE att_leave_type (
  id            BIGINT       NOT NULL,                            -- 主键: 雪花ID
  tenant_id     BIGINT       NOT NULL,                            -- 租户ID
  name          VARCHAR(64)  NOT NULL,                            -- 假种名称
  code          VARCHAR(64)  NOT NULL,                            -- 假种编码
  rules         JSONB,                                            -- 额度/审批规则(json)
  created_by    BIGINT       NOT NULL DEFAULT 0,                  -- 创建人ID,0=系统
  updated_by    BIGINT       NOT NULL DEFAULT 0,                  -- 更新人ID
  created_at    TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,  -- 创建时间(UTC)
  updated_at    TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,  -- 更新时间(UTC,应用层维护)
  deleted       SMALLINT     NOT NULL DEFAULT 0,                  -- 逻辑删除 0=否 1=是
  version       INTEGER      NOT NULL DEFAULT 0,                  -- 乐观锁版本号
  CONSTRAINT pk_att_leave_type PRIMARY KEY (id),
  CONSTRAINT uk_att_leave_type_code UNIQUE (tenant_id, code)
);

-- 假期额度
CREATE TABLE att_leave_quota (
  id            BIGINT       NOT NULL,                            -- 主键: 雪花ID
  tenant_id     BIGINT       NOT NULL,                            -- 租户ID
  emp_id        BIGINT       NOT NULL,                            -- 员工ID(逻辑外键 emp_employee.id)
  leave_type_id BIGINT       NOT NULL,                            -- 假种ID(逻辑外键 att_leave_type.id)
  period        VARCHAR(16)  NOT NULL,                            -- 周期(如 2026)
  total_quota   NUMERIC(6,2) NOT NULL DEFAULT 0,                  -- 总额度(天)
  used_quota    NUMERIC(6,2) NOT NULL DEFAULT 0,                  -- 已用额度(天)
  created_by    BIGINT       NOT NULL DEFAULT 0,                  -- 创建人ID,0=系统
  updated_by    BIGINT       NOT NULL DEFAULT 0,                  -- 更新人ID
  created_at    TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,  -- 创建时间(UTC)
  updated_at    TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,  -- 更新时间(UTC,应用层维护)
  deleted       SMALLINT     NOT NULL DEFAULT 0,                  -- 逻辑删除 0=否 1=是
  version       INTEGER      NOT NULL DEFAULT 0,                  -- 乐观锁版本号
  CONSTRAINT pk_att_leave_quota PRIMARY KEY (id),
  CONSTRAINT uk_att_leave_quota UNIQUE (tenant_id, emp_id, leave_type_id, period)
);

-- 额度流水
CREATE TABLE att_leave_ledger (
  id            BIGINT       NOT NULL,                            -- 主键: 雪花ID
  tenant_id     BIGINT       NOT NULL,                            -- 租户ID
  emp_id        BIGINT       NOT NULL,                            -- 员工ID(逻辑外键 emp_employee.id)
  leave_type_id BIGINT       NOT NULL,                            -- 假种ID(逻辑外键 att_leave_type.id)
  change_amount NUMERIC(6,2) NOT NULL,                            -- 变动额度(天,正增负减)
  biz_ref       VARCHAR(64),                                      -- 业务引用(请假单号)
  created_by    BIGINT       NOT NULL DEFAULT 0,                  -- 创建人ID,0=系统
  updated_by    BIGINT       NOT NULL DEFAULT 0,                  -- 更新人ID
  created_at    TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,  -- 创建时间(UTC)
  updated_at    TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,  -- 更新时间(UTC,应用层维护)
  deleted       SMALLINT     NOT NULL DEFAULT 0,                  -- 逻辑删除 0=否 1=是
  version       INTEGER      NOT NULL DEFAULT 0,                  -- 乐观锁版本号
  CONSTRAINT pk_att_leave_ledger PRIMARY KEY (id)
);
CREATE INDEX idx_att_leave_ledger_emp ON att_leave_ledger (tenant_id, emp_id, leave_type_id);

-- 请假单(关联 flow)
CREATE TABLE att_leave_request (
  id            BIGINT       NOT NULL,                            -- 主键: 雪花ID
  tenant_id     BIGINT       NOT NULL,                            -- 租户ID
  emp_id        BIGINT       NOT NULL,                            -- 员工ID(逻辑外键 emp_employee.id)
  leave_type_id BIGINT       NOT NULL,                            -- 假种ID(逻辑外键 att_leave_type.id)
  biz_key       VARCHAR(64)  NOT NULL,                            -- 业务单号(流程幂等键)
  start_time    TIMESTAMP(3) NOT NULL,                            -- 请假开始时间(UTC)
  end_time      TIMESTAMP(3) NOT NULL,                            -- 请假结束时间(UTC)
  days          NUMERIC(6,2) NOT NULL,                            -- 请假天数
  reason        VARCHAR(255),                                     -- 请假事由
  status        VARCHAR(16)  NOT NULL DEFAULT 'DRAFT',            -- 状态 DRAFT/PROCESSING/APPROVED/REJECTED/CANCELLED
  proc_inst_id  VARCHAR(64),                                      -- 流程实例ID
  created_by    BIGINT       NOT NULL DEFAULT 0,                  -- 创建人ID,0=系统
  updated_by    BIGINT       NOT NULL DEFAULT 0,                  -- 更新人ID
  created_at    TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,  -- 创建时间(UTC)
  updated_at    TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,  -- 更新时间(UTC,应用层维护)
  deleted       SMALLINT     NOT NULL DEFAULT 0,                  -- 逻辑删除 0=否 1=是
  version       INTEGER      NOT NULL DEFAULT 0,                  -- 乐观锁版本号
  CONSTRAINT pk_att_leave_request PRIMARY KEY (id),
  CONSTRAINT uk_att_leave_biz UNIQUE (tenant_id, biz_key)
);
CREATE INDEX idx_att_leave_emp ON att_leave_request (tenant_id, emp_id, status);

-- 加班单
CREATE TABLE att_overtime_request (
  id            BIGINT       NOT NULL,                            -- 主键: 雪花ID
  tenant_id     BIGINT       NOT NULL,                            -- 租户ID
  emp_id        BIGINT       NOT NULL,                            -- 员工ID(逻辑外键 emp_employee.id)
  biz_key       VARCHAR(64)  NOT NULL,                            -- 业务单号(流程幂等键)
  start_time    TIMESTAMP(3) NOT NULL,                            -- 加班开始时间(UTC)
  end_time      TIMESTAMP(3) NOT NULL,                            -- 加班结束时间(UTC)
  hours         NUMERIC(6,2) NOT NULL,                            -- 加班时长(小时)
  reason        VARCHAR(255),                                     -- 加班事由
  status        VARCHAR(16)  NOT NULL DEFAULT 'DRAFT',            -- 状态
  proc_inst_id  VARCHAR(64),                                      -- 流程实例ID
  created_by    BIGINT       NOT NULL DEFAULT 0,                  -- 创建人ID,0=系统
  updated_by    BIGINT       NOT NULL DEFAULT 0,                  -- 更新人ID
  created_at    TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,  -- 创建时间(UTC)
  updated_at    TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,  -- 更新时间(UTC,应用层维护)
  deleted       SMALLINT     NOT NULL DEFAULT 0,                  -- 逻辑删除 0=否 1=是
  version       INTEGER      NOT NULL DEFAULT 0,                  -- 乐观锁版本号
  CONSTRAINT pk_att_overtime_request PRIMARY KEY (id),
  CONSTRAINT uk_att_overtime_biz UNIQUE (tenant_id, biz_key)
);

-- 出差单
CREATE TABLE att_trip_request (
  id            BIGINT       NOT NULL,                            -- 主键: 雪花ID
  tenant_id     BIGINT       NOT NULL,                            -- 租户ID
  emp_id        BIGINT       NOT NULL,                            -- 员工ID(逻辑外键 emp_employee.id)
  biz_key       VARCHAR(64)  NOT NULL,                            -- 业务单号(流程幂等键)
  start_time    TIMESTAMP(3) NOT NULL,                            -- 出差开始时间(UTC)
  end_time      TIMESTAMP(3) NOT NULL,                            -- 出差结束时间(UTC)
  destination   VARCHAR(128) NOT NULL,                            -- 出差目的地
  reason        VARCHAR(255),                                     -- 出差事由
  status        VARCHAR(16)  NOT NULL DEFAULT 'DRAFT',            -- 状态
  proc_inst_id  VARCHAR(64),                                      -- 流程实例ID
  created_by    BIGINT       NOT NULL DEFAULT 0,                  -- 创建人ID,0=系统
  updated_by    BIGINT       NOT NULL DEFAULT 0,                  -- 更新人ID
  created_at    TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,  -- 创建时间(UTC)
  updated_at    TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,  -- 更新时间(UTC,应用层维护)
  deleted       SMALLINT     NOT NULL DEFAULT 0,                  -- 逻辑删除 0=否 1=是
  version       INTEGER      NOT NULL DEFAULT 0,                  -- 乐观锁版本号
  CONSTRAINT pk_att_trip_request PRIMARY KEY (id),
  CONSTRAINT uk_att_trip_biz UNIQUE (tenant_id, biz_key)
);

-- 外出/居家办公申请(ATT-F-018,权限点直批,不走 Flowable)
CREATE TABLE att_out_office (
  id            BIGINT       NOT NULL,                            -- 主键: 雪花ID
  tenant_id     BIGINT       NOT NULL,                            -- 租户ID
  emp_id        BIGINT       NOT NULL,                            -- 员工ID(逻辑外键 emp_employee.id)
  type          VARCHAR(8)   NOT NULL,                            -- 类型 OUT=外出 WFH=居家办公
  start_time    TIMESTAMP(3) NOT NULL,                            -- 开始时间(UTC)
  end_time      TIMESTAMP(3) NOT NULL,                            -- 结束时间(UTC)
  reason        VARCHAR(255),                                     -- 事由
  status        VARCHAR(16)  NOT NULL,                            -- 状态 PROCESSING/APPROVED/REJECTED
  approver_id   BIGINT,                                           -- 审批人员工ID(审批通过/驳回时回填)
  created_by    BIGINT       NOT NULL DEFAULT 0,                  -- 创建人ID,0=系统
  updated_by    BIGINT       NOT NULL DEFAULT 0,                  -- 更新人ID
  created_at    TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,  -- 创建时间(UTC)
  updated_at    TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,  -- 更新时间(UTC,应用层维护)
  deleted       SMALLINT     NOT NULL DEFAULT 0,                  -- 逻辑删除 0=否 1=是
  version       INTEGER      NOT NULL DEFAULT 0,                  -- 乐观锁版本号
  CONSTRAINT pk_att_out_office PRIMARY KEY (id)
);
CREATE INDEX idx_att_out_office_emp ON att_out_office (tenant_id, emp_id, start_time);

-- 补卡单
CREATE TABLE att_fix_request (
  id            BIGINT       NOT NULL,                            -- 主键: 雪花ID
  tenant_id     BIGINT       NOT NULL,                            -- 租户ID
  emp_id        BIGINT       NOT NULL,                            -- 员工ID(逻辑外键 emp_employee.id)
  biz_key       VARCHAR(64)  NOT NULL,                            -- 业务单号(流程幂等键)
  work_date     DATE         NOT NULL,                            -- 补卡日期
  fix_time      TIMESTAMP(3) NOT NULL,                            -- 补卡时间(UTC)
  type          VARCHAR(8)   NOT NULL,                            -- 补卡类型 IN/OUT
  reason        VARCHAR(255),                                     -- 补卡原因
  status        VARCHAR(16)  NOT NULL DEFAULT 'DRAFT',            -- 状态
  proc_inst_id  VARCHAR(64),                                      -- 流程实例ID
  created_by    BIGINT       NOT NULL DEFAULT 0,                  -- 创建人ID,0=系统
  updated_by    BIGINT       NOT NULL DEFAULT 0,                  -- 更新人ID
  created_at    TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,  -- 创建时间(UTC)
  updated_at    TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,  -- 更新时间(UTC,应用层维护)
  deleted       SMALLINT     NOT NULL DEFAULT 0,                  -- 逻辑删除 0=否 1=是
  version       INTEGER      NOT NULL DEFAULT 0,                  -- 乐观锁版本号
  CONSTRAINT pk_att_fix_request PRIMARY KEY (id),
  CONSTRAINT uk_att_fix_biz UNIQUE (tenant_id, biz_key)
);

-- 薪酬取数周期
CREATE TABLE att_payroll_cycle (
  id            BIGINT       NOT NULL,                            -- 主键: 雪花ID
  tenant_id     BIGINT       NOT NULL,                            -- 租户ID
  period_code   VARCHAR(16)  NOT NULL,                            -- 周期编码(如 2026-01)
  start_date    DATE         NOT NULL,                            -- 周期开始日
  end_date      DATE         NOT NULL,                            -- 周期结束日
  status        VARCHAR(16)  NOT NULL DEFAULT 'OPEN',             -- 状态 OPEN/CLOSED
  created_by    BIGINT       NOT NULL DEFAULT 0,                  -- 创建人ID,0=系统
  updated_by    BIGINT       NOT NULL DEFAULT 0,                  -- 更新人ID
  created_at    TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,  -- 创建时间(UTC)
  updated_at    TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,  -- 更新时间(UTC,应用层维护)
  deleted       SMALLINT     NOT NULL DEFAULT 0,                  -- 逻辑删除 0=否 1=是
  version       INTEGER      NOT NULL DEFAULT 0,                  -- 乐观锁版本号
  CONSTRAINT pk_att_payroll_cycle PRIMARY KEY (id),
  CONSTRAINT uk_att_payroll_cycle UNIQUE (tenant_id, period_code)
);

-- 月结批次
CREATE TABLE att_monthly_closing (
  id            BIGINT       NOT NULL,                            -- 主键: 雪花ID
  tenant_id     BIGINT       NOT NULL,                            -- 租户ID
  period_code   VARCHAR(16)  NOT NULL,                            -- 周期编码(如 2026-01)
  status        VARCHAR(16)  NOT NULL DEFAULT 'INIT',             -- 状态 INIT/RUNNING/DONE/FAILED
  closed_at     TIMESTAMP(3),                                     -- 月结完成时间(UTC)
  created_by    BIGINT       NOT NULL DEFAULT 0,                  -- 创建人ID,0=系统
  updated_by    BIGINT       NOT NULL DEFAULT 0,                  -- 更新人ID
  created_at    TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,  -- 创建时间(UTC)
  updated_at    TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,  -- 更新时间(UTC,应用层维护)
  deleted       SMALLINT     NOT NULL DEFAULT 0,                  -- 逻辑删除 0=否 1=是
  version       INTEGER      NOT NULL DEFAULT 0,                  -- 乐观锁版本号
  CONSTRAINT pk_att_monthly_closing PRIMARY KEY (id),
  CONSTRAINT uk_att_closing_period UNIQUE (tenant_id, period_code)
);
CREATE INDEX idx_att_closing_status ON att_monthly_closing (tenant_id, status);

-- 月结调整
CREATE TABLE att_closing_adjustment (
  id            BIGINT       NOT NULL,                            -- 主键: 雪花ID
  tenant_id     BIGINT       NOT NULL,                            -- 租户ID
  emp_id        BIGINT       NOT NULL,                            -- 员工ID(逻辑外键 emp_employee.id)
  closing_id    BIGINT       NOT NULL,                            -- 月结批次ID(逻辑外键 att_monthly_closing.id)
  adjust_type   VARCHAR(16)  NOT NULL,                            -- 调整类型
  amount        NUMERIC(6,2) NOT NULL DEFAULT 0,                  -- 调整量
  reason        VARCHAR(255),                                     -- 调整原因
  created_by    BIGINT       NOT NULL DEFAULT 0,                  -- 创建人ID,0=系统
  updated_by    BIGINT       NOT NULL DEFAULT 0,                  -- 更新人ID
  created_at    TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,  -- 创建时间(UTC)
  updated_at    TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,  -- 更新时间(UTC,应用层维护)
  deleted       SMALLINT     NOT NULL DEFAULT 0,                  -- 逻辑删除 0=否 1=是
  version       INTEGER      NOT NULL DEFAULT 0,                  -- 乐观锁版本号
  CONSTRAINT pk_att_closing_adjustment PRIMARY KEY (id)
);
CREATE INDEX idx_att_closing_adj ON att_closing_adjustment (tenant_id, emp_id, closing_id);

-- =====================================================================
-- 域 5: rcu_db 招聘
-- =====================================================================
CREATE SCHEMA IF NOT EXISTS rcu_db;
SET search_path TO rcu_db;

-- 招聘需求
CREATE TABLE rcu_requisition (
  id            BIGINT       NOT NULL,                            -- 主键: 雪花ID
  tenant_id     BIGINT       NOT NULL,                            -- 租户ID
  req_no        VARCHAR(64)  NOT NULL,                            -- 需求编号
  dept_id       BIGINT       NOT NULL,                            -- 部门ID(逻辑外键 org_dept.id)
  position_id   BIGINT       NOT NULL,                            -- 岗位ID(逻辑外键 org_position.id)
  headcount     INTEGER      NOT NULL DEFAULT 1,                  -- 招聘人数
  reason        VARCHAR(255),                                     -- 需求原因
  status        VARCHAR(16)  NOT NULL DEFAULT 'DRAFT',            -- 状态 DRAFT/APPROVING/OPEN/CLOSED
  published     SMALLINT     NOT NULL DEFAULT 0,                  -- 官网发布 0=否 1=是(RCS-F-004,仅OPEN可发布)
  created_by    BIGINT       NOT NULL DEFAULT 0,                  -- 创建人ID,0=系统
  updated_by    BIGINT       NOT NULL DEFAULT 0,                  -- 更新人ID
  created_at    TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,  -- 创建时间(UTC)
  updated_at    TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,  -- 更新时间(UTC,应用层维护)
  deleted       SMALLINT     NOT NULL DEFAULT 0,                  -- 逻辑删除 0=否 1=是
  version       INTEGER      NOT NULL DEFAULT 0,                  -- 乐观锁版本号
  CONSTRAINT pk_rcu_requisition PRIMARY KEY (id),
  CONSTRAINT uk_rcu_requisition_no UNIQUE (tenant_id, req_no)
);
CREATE INDEX idx_rcu_requisition_dept ON rcu_requisition (tenant_id, dept_id);

-- 职位
CREATE TABLE rcu_job_opening (
  id             BIGINT       NOT NULL,                           -- 主键: 雪花ID
  tenant_id      BIGINT       NOT NULL,                           -- 租户ID
  requisition_id BIGINT,                                          -- 需求ID(逻辑外键 rcu_requisition.id)
  code           VARCHAR(64)  NOT NULL,                           -- 职位编码
  title          VARCHAR(128) NOT NULL,                           -- 职位名称
  description    TEXT,                                            -- 职位描述
  status         VARCHAR(16)  NOT NULL DEFAULT 'DRAFT',           -- 状态 DRAFT/PUBLISHED/CLOSED
  publish_at     TIMESTAMP(3),                                    -- 发布时间(UTC)
  created_by     BIGINT       NOT NULL DEFAULT 0,                 -- 创建人ID,0=系统
  updated_by     BIGINT       NOT NULL DEFAULT 0,                 -- 更新人ID
  created_at     TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP, -- 创建时间(UTC)
  updated_at     TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP, -- 更新时间(UTC,应用层维护)
  deleted        SMALLINT     NOT NULL DEFAULT 0,                 -- 逻辑删除 0=否 1=是
  version        INTEGER      NOT NULL DEFAULT 0,                 -- 乐观锁版本号
  CONSTRAINT pk_rcu_job_opening PRIMARY KEY (id),
  CONSTRAINT uk_rcu_opening_code UNIQUE (tenant_id, code)
);
CREATE INDEX idx_rcu_opening_status ON rcu_job_opening (tenant_id, status);

-- 招聘渠道
CREATE TABLE rcu_channel (
  id            BIGINT       NOT NULL,                            -- 主键: 雪花ID
  tenant_id     BIGINT       NOT NULL,                            -- 租户ID
  name          VARCHAR(64)  NOT NULL,                            -- 渠道名称
  code          VARCHAR(64)  NOT NULL,                            -- 渠道编码
  created_by    BIGINT       NOT NULL DEFAULT 0,                  -- 创建人ID,0=系统
  updated_by    BIGINT       NOT NULL DEFAULT 0,                  -- 更新人ID
  created_at    TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,  -- 创建时间(UTC)
  updated_at    TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,  -- 更新时间(UTC,应用层维护)
  deleted       SMALLINT     NOT NULL DEFAULT 0,                  -- 逻辑删除 0=否 1=是
  version       INTEGER      NOT NULL DEFAULT 0,                  -- 乐观锁版本号
  CONSTRAINT pk_rcu_channel PRIMARY KEY (id),
  CONSTRAINT uk_rcu_channel_code UNIQUE (tenant_id, code)
);

-- 候选人
CREATE TABLE rcu_candidate (
  id            BIGINT       NOT NULL,                            -- 主键: 雪花ID
  tenant_id     BIGINT       NOT NULL,                            -- 租户ID
  name          VARCHAR(64)  NOT NULL,                            -- 候选人姓名
  mobile_cipher VARCHAR(256),                                     -- 手机号密文(逻辑列 mobile)
  mobile_mask   VARCHAR(32),                                      -- 手机号掩码
  email         VARCHAR(128),                                     -- 邮箱
  status        VARCHAR(16)  NOT NULL DEFAULT 'NEW',              -- 状态 NEW/SCREENING/INTERVIEWING/OFFERED/HIRED/REJECTED
  created_by    BIGINT       NOT NULL DEFAULT 0,                  -- 创建人ID,0=系统
  updated_by    BIGINT       NOT NULL DEFAULT 0,                  -- 更新人ID
  created_at    TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,  -- 创建时间(UTC)
  updated_at    TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,  -- 更新时间(UTC,应用层维护)
  deleted       SMALLINT     NOT NULL DEFAULT 0,                  -- 逻辑删除 0=否 1=是
  version       INTEGER      NOT NULL DEFAULT 0,                  -- 乐观锁版本号
  CONSTRAINT pk_rcu_candidate PRIMARY KEY (id)
);
CREATE INDEX idx_rcu_candidate_status ON rcu_candidate (tenant_id, status);

-- 简历
CREATE TABLE rcu_resume (
  id            BIGINT       NOT NULL,                            -- 主键: 雪花ID
  tenant_id     BIGINT       NOT NULL,                            -- 租户ID
  candidate_id  BIGINT       NOT NULL,                            -- 候选人ID(逻辑外键 rcu_candidate.id)
  file_id       BIGINT,                                           -- 简历附件ID(逻辑外键 sys_file_meta.id)
  parse_result  JSONB,                                            -- 简历解析结果(json)
  retention_until DATE,                                           -- 简历保留截止日(RCS-F-022,创建时按租户配置计算)
  created_by    BIGINT       NOT NULL DEFAULT 0,                  -- 创建人ID,0=系统
  updated_by    BIGINT       NOT NULL DEFAULT 0,                  -- 更新人ID
  created_at    TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,  -- 创建时间(UTC)
  updated_at    TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,  -- 更新时间(UTC,应用层维护)
  deleted       SMALLINT     NOT NULL DEFAULT 0,                  -- 逻辑删除 0=否 1=是
  version       INTEGER      NOT NULL DEFAULT 0,                  -- 乐观锁版本号
  CONSTRAINT pk_rcu_resume PRIMARY KEY (id)
);
CREATE INDEX idx_rcu_resume_candidate ON rcu_resume (tenant_id, candidate_id);
CREATE INDEX idx_rcu_resume_retention ON rcu_resume (tenant_id, retention_until);

-- 简历解析结果(RCS-F-007)
CREATE TABLE rcu_resume_parse (
  id              BIGINT       NOT NULL,                            -- 主键: 雪花ID
  tenant_id       BIGINT       NOT NULL,                            -- 租户ID
  resume_id       BIGINT       NOT NULL,                            -- 简历ID(逻辑外键 rcu_resume.id)
  candidate_id    BIGINT,                                           -- 候选人ID(逻辑外键 rcu_candidate.id)
  parsed_name     VARCHAR(64),                                      -- 解析姓名(启发式,需人工核对)
  parsed_mobile   VARCHAR(32),                                      -- 解析手机号(大陆11位)
  parsed_email    VARCHAR(128),                                     -- 解析邮箱
  education_json  TEXT,                                             -- 教育经历(json数组)
  experience_json TEXT,                                             -- 工作/项目经历(json数组)
  parse_status    VARCHAR(16)  NOT NULL,                            -- 解析状态 OK/RULE_LIMITED(规则解析,需人工核对)
  parse_error     VARCHAR(255),                                     -- 解析错误说明
  created_by      BIGINT       NOT NULL DEFAULT 0,                  -- 创建人ID,0=系统
  updated_by      BIGINT       NOT NULL DEFAULT 0,                  -- 更新人ID
  created_at      TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,  -- 创建时间(UTC)
  updated_at      TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,  -- 更新时间(UTC,应用层维护)
  deleted         SMALLINT     NOT NULL DEFAULT 0,                  -- 逻辑删除 0=否 1=是
  version         INTEGER      NOT NULL DEFAULT 0,                  -- 乐观锁版本号
  CONSTRAINT pk_rcu_resume_parse PRIMARY KEY (id),
  CONSTRAINT uk_rcu_resume_parse UNIQUE (tenant_id, resume_id)
);
CREATE INDEX idx_rcu_resume_parse_candidate ON rcu_resume_parse (tenant_id, candidate_id);

-- 投递
CREATE TABLE rcu_application (
  id            BIGINT       NOT NULL,                            -- 主键: 雪花ID
  tenant_id     BIGINT       NOT NULL,                            -- 租户ID
  candidate_id  BIGINT       NOT NULL,                            -- 候选人ID(逻辑外键 rcu_candidate.id)
  opening_id    BIGINT       NOT NULL,                            -- 职位ID(逻辑外键 rcu_job_opening.id)
  channel_id    BIGINT,                                           -- 渠道ID(逻辑外键 rcu_channel.id)
  stage         VARCHAR(16)  NOT NULL DEFAULT 'APPLIED',          -- 阶段 APPLIED/SCREEN/INTERVIEW/OFFER/HIRED
  status        VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE',           -- 状态 ACTIVE/REJECTED/WITHDRAWN
  reason        VARCHAR(32),                                      -- 淘汰原因 RCS-F-012 SKILL_MISMATCH/SALARY_MISMATCH/ATTITUDE/HEADCOUNT_CLOSED/WITHDRAWN/OTHER
  reason_note   VARCHAR(255),                                     -- 原因补充(OTHER 必填)
  created_by    BIGINT       NOT NULL DEFAULT 0,                  -- 创建人ID,0=系统
  updated_by    BIGINT       NOT NULL DEFAULT 0,                  -- 更新人ID
  created_at    TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,  -- 创建时间(UTC)
  updated_at    TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,  -- 更新时间(UTC,应用层维护)
  deleted       SMALLINT     NOT NULL DEFAULT 0,                  -- 逻辑删除 0=否 1=是
  version       INTEGER      NOT NULL DEFAULT 0,                  -- 乐观锁版本号
  CONSTRAINT pk_rcu_application PRIMARY KEY (id),
  CONSTRAINT uk_rcu_application UNIQUE (tenant_id, candidate_id, opening_id)
);

-- 阶段流转日志
CREATE TABLE rcu_stage_log (
  id             BIGINT       NOT NULL,                           -- 主键: 雪花ID
  tenant_id      BIGINT       NOT NULL,                           -- 租户ID
  application_id BIGINT       NOT NULL,                           -- 投递ID(逻辑外键 rcu_application.id)
  from_stage     VARCHAR(16),                                     -- 原阶段
  to_stage       VARCHAR(16)  NOT NULL,                           -- 新阶段
  remark         VARCHAR(255),                                    -- 备注
  created_by     BIGINT       NOT NULL DEFAULT 0,                 -- 创建人ID(操作人)
  updated_by     BIGINT       NOT NULL DEFAULT 0,                 -- 更新人ID
  created_at     TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP, -- 创建时间(UTC)
  updated_at     TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP, -- 更新时间(UTC,应用层维护)
  deleted        SMALLINT     NOT NULL DEFAULT 0,                 -- 逻辑删除 0=否 1=是
  version        INTEGER      NOT NULL DEFAULT 0,                 -- 乐观锁版本号
  CONSTRAINT pk_rcu_stage_log PRIMARY KEY (id)
);
CREATE INDEX idx_rcu_stage_log_app ON rcu_stage_log (tenant_id, application_id, created_at);

-- 面试安排
CREATE TABLE rcu_interview (
  id              BIGINT       NOT NULL,                          -- 主键: 雪花ID
  tenant_id       BIGINT       NOT NULL,                          -- 租户ID
  application_id  BIGINT       NOT NULL,                          -- 投递ID(逻辑外键 rcu_application.id)
  round           INTEGER      NOT NULL DEFAULT 1,                -- 面试轮次
  planned_at      TIMESTAMP(3),                                   -- 计划面试时间(UTC)
  interviewer_ids JSONB,                                          -- 面试官ID集合
  status          VARCHAR(16)  NOT NULL DEFAULT 'SCHEDULED',      -- 状态 SCHEDULED/DONE/CANCELLED
  created_by      BIGINT       NOT NULL DEFAULT 0,                -- 创建人ID,0=系统
  updated_by      BIGINT       NOT NULL DEFAULT 0,                -- 更新人ID
  created_at      TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP, -- 创建时间(UTC)
  updated_at      TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP, -- 更新时间(UTC,应用层维护)
  deleted         SMALLINT     NOT NULL DEFAULT 0,                -- 逻辑删除 0=否 1=是
  version         INTEGER      NOT NULL DEFAULT 0,                -- 乐观锁版本号
  CONSTRAINT pk_rcu_interview PRIMARY KEY (id)
);
CREATE INDEX idx_rcu_interview_app ON rcu_interview (tenant_id, application_id, planned_at);

-- 面试评价
CREATE TABLE rcu_interview_eval (
  id            BIGINT       NOT NULL,                            -- 主键: 雪花ID
  tenant_id     BIGINT       NOT NULL,                            -- 租户ID
  interview_id  BIGINT       NOT NULL,                            -- 面试ID(逻辑外键 rcu_interview.id)
  evaluator_id  BIGINT       NOT NULL,                            -- 评价人用户ID(逻辑外键 auth_user.id)
  score         INTEGER,                                          -- 评分
  comment       VARCHAR(512),                                     -- 评价意见
  pass          SMALLINT,                                         -- 是否通过 1=通过 0=不通过
  created_by    BIGINT       NOT NULL DEFAULT 0,                  -- 创建人ID,0=系统
  updated_by    BIGINT       NOT NULL DEFAULT 0,                  -- 更新人ID
  created_at    TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,  -- 创建时间(UTC)
  updated_at    TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,  -- 更新时间(UTC,应用层维护)
  deleted       SMALLINT     NOT NULL DEFAULT 0,                  -- 逻辑删除 0=否 1=是
  version       INTEGER      NOT NULL DEFAULT 0,                  -- 乐观锁版本号
  CONSTRAINT pk_rcu_interview_eval PRIMARY KEY (id)
);
CREATE INDEX idx_rcu_interview_eval ON rcu_interview_eval (tenant_id, interview_id, evaluator_id);

-- Offer
CREATE TABLE rcu_offer (
  id                   BIGINT       NOT NULL,                     -- 主键: 雪花ID
  tenant_id            BIGINT       NOT NULL,                     -- 租户ID
  application_id       BIGINT       NOT NULL,                     -- 投递ID(逻辑外键 rcu_application.id)
  offer_no             VARCHAR(64)  NOT NULL,                     -- Offer编号
  salary_band_check    SMALLINT,                                  -- 薪资带校验结果 1=通过 0=超带
  salary_amount_cipher VARCHAR(256),                              -- Offer薪资密文(逻辑列 salary_amount)
  salary_amount_mask   VARCHAR(64),                               -- Offer薪资掩码
  status               VARCHAR(16)  NOT NULL DEFAULT 'DRAFT',     -- 状态 DRAFT/SENT/ACCEPTED/REJECTED
  proc_inst_id         VARCHAR(64),                               -- 流程实例ID
  receipt_type         VARCHAR(16),                               -- 候选人回执类型 RCS-F-018 ACCEPTED/DECLINED
  receipt_note         VARCHAR(512),                              -- 回执备注
  receipt_at           TIMESTAMP(3),                              -- 回执登记时间(UTC)
  created_by           BIGINT       NOT NULL DEFAULT 0,           -- 创建人ID,0=系统
  updated_by           BIGINT       NOT NULL DEFAULT 0,           -- 更新人ID
  created_at           TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP, -- 创建时间(UTC)
  updated_at           TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP, -- 更新时间(UTC,应用层维护)
  deleted              SMALLINT     NOT NULL DEFAULT 0,           -- 逻辑删除 0=否 1=是
  version              INTEGER      NOT NULL DEFAULT 0,           -- 乐观锁版本号
  CONSTRAINT pk_rcu_offer PRIMARY KEY (id),
  CONSTRAINT uk_rcu_offer_no UNIQUE (tenant_id, offer_no)
);
CREATE INDEX idx_rcu_offer_app ON rcu_offer (tenant_id, application_id);

-- Offer 发送日志(RCS-F-018)：每次发送留痕，FAILED 不透出为 SENT
CREATE TABLE rcu_offer_send_log (
  id            BIGINT       NOT NULL,                            -- 主键: 雪花ID
  tenant_id     BIGINT       NOT NULL,                            -- 租户ID
  offer_id      BIGINT       NOT NULL,                            -- OfferID(逻辑外键 rcu_offer.id)
  channel       VARCHAR(16)  NOT NULL,                            -- 发送渠道 EMAIL/WECOM/DINGTALK
  status        VARCHAR(16)  NOT NULL,                            -- 发送状态 SENT/FAILED
  error         VARCHAR(512),                                     -- 失败原因(FAILED 时透出)
  sent_at       TIMESTAMP(3),                                     -- 发送成功时间(UTC)
  created_by    BIGINT       NOT NULL DEFAULT 0,                  -- 创建人ID,0=系统
  updated_by    BIGINT       NOT NULL DEFAULT 0,                  -- 更新人ID
  created_at    TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,  -- 创建时间(UTC)
  updated_at    TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,  -- 更新时间(UTC,应用层维护)
  deleted       SMALLINT     NOT NULL DEFAULT 0,                  -- 逻辑删除 0=否 1=是
  version       INTEGER      NOT NULL DEFAULT 0,                  -- 乐观锁版本号
  CONSTRAINT pk_rcu_offer_send_log PRIMARY KEY (id)
);
CREATE INDEX idx_rcu_offer_send_log ON rcu_offer_send_log (tenant_id, offer_id, created_at);

-- 人才库标签
CREATE TABLE rcu_talent_pool_tag (
  id            BIGINT       NOT NULL,                            -- 主键: 雪花ID
  tenant_id     BIGINT       NOT NULL,                            -- 租户ID
  candidate_id  BIGINT       NOT NULL,                            -- 候选人ID(逻辑外键 rcu_candidate.id)
  tag           VARCHAR(64)  NOT NULL,                            -- 标签
  created_by    BIGINT       NOT NULL DEFAULT 0,                  -- 创建人ID,0=系统
  updated_by    BIGINT       NOT NULL DEFAULT 0,                  -- 更新人ID
  created_at    TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,  -- 创建时间(UTC)
  updated_at    TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,  -- 更新时间(UTC,应用层维护)
  deleted       SMALLINT     NOT NULL DEFAULT 0,                  -- 逻辑删除 0=否 1=是
  version       INTEGER      NOT NULL DEFAULT 0,                  -- 乐观锁版本号
  CONSTRAINT pk_rcu_talent_pool_tag PRIMARY KEY (id),
  CONSTRAINT uk_rcu_talent_tag UNIQUE (tenant_id, candidate_id, tag)
);

-- 官网租户域名映射(批 7 #13)：域名命中自动定租户（PublicController X-Site-Domain/Host 头）
CREATE TABLE rcu_site_domain (
  id          BIGINT       NOT NULL,                            -- 主键: 雪花ID
  tenant_id   BIGINT       NOT NULL,                            -- 租户ID
  domain      VARCHAR(128) NOT NULL,                            -- 站点域名(小写,不含协议与端口)
  status      VARCHAR(16)  NOT NULL DEFAULT 'ENABLED',          -- 状态 ENABLED/DISABLED
  created_by  BIGINT       NOT NULL DEFAULT 0,                  -- 创建人ID,0=系统
  updated_by  BIGINT       NOT NULL DEFAULT 0,                  -- 更新人ID
  created_at  TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,  -- 创建时间(UTC)
  updated_at  TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,  -- 更新时间(UTC,应用层维护)
  deleted     SMALLINT     NOT NULL DEFAULT 0,                  -- 逻辑删除 0=否 1=是
  version     INTEGER      NOT NULL DEFAULT 0,                  -- 乐观锁版本号
  CONSTRAINT pk_rcu_site_domain PRIMARY KEY (id),
  CONSTRAINT uk_rcu_site_domain UNIQUE (domain)
);
CREATE INDEX idx_rcu_site_domain_tenant ON rcu_site_domain (tenant_id);

-- =====================================================================
-- 域 6: cmp_db 薪酬
-- =====================================================================
CREATE SCHEMA IF NOT EXISTS cmp_db;
SET search_path TO cmp_db;

-- 薪酬项(公式表达式版本化, Aviator 沙箱求值, 按 pay_group 定义)
CREATE TABLE cmp_pay_item (
  id            BIGINT       NOT NULL,                            -- 主键: 雪花ID
  tenant_id     BIGINT       NOT NULL,                            -- 租户ID
  pay_group_id  BIGINT       NOT NULL DEFAULT 0,                  -- 薪资组ID(逻辑外键 cmp_pay_group.id),0=全局
  name          VARCHAR(64)  NOT NULL,                            -- 薪酬项名称
  code          VARCHAR(64)  NOT NULL,                            -- 薪酬项编码
  category      VARCHAR(16)  NOT NULL DEFAULT 'EARNING',          -- 类别 EARNING/DEDUCTION/TAX/NET
  seq           INTEGER      NOT NULL DEFAULT 0,                  -- 求值顺序(小者优先)
  version       INTEGER      NOT NULL DEFAULT 1,                  -- 版本号
  formula       VARCHAR(512),                                     -- 计算公式(表达式,Aviator 语法,可空;NET 缺省=sum(EARNING)-sum(DEDUCTION)-sum(TAX))
  status        SMALLINT     NOT NULL DEFAULT 1,                  -- 状态 1=启用 0=停用
  created_by    BIGINT       NOT NULL DEFAULT 0,                  -- 创建人ID,0=系统
  updated_by    BIGINT       NOT NULL DEFAULT 0,                  -- 更新人ID
  created_at    TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,  -- 创建时间(UTC)
  updated_at    TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,  -- 更新时间(UTC,应用层维护)
  deleted       SMALLINT     NOT NULL DEFAULT 0,                  -- 逻辑删除 0=否 1=是
  CONSTRAINT pk_cmp_pay_item PRIMARY KEY (id),
  CONSTRAINT uk_cmp_pay_item UNIQUE (tenant_id, pay_group_id, code, version)
);

-- 算薪任务(F-006 逐员工监控)
CREATE TABLE cmp_calc_task (
  id            BIGINT       NOT NULL,                            -- 主键: 雪花ID
  tenant_id     BIGINT       NOT NULL,                            -- 租户ID
  run_id        BIGINT       NOT NULL,                            -- 算薪批次ID(逻辑外键 cmp_payroll_run.id)
  emp_id        BIGINT       NOT NULL,                            -- 员工ID(逻辑外键 emp_employee.id)
  status        VARCHAR(16)  NOT NULL DEFAULT 'RUNNING',          -- 任务状态 RUNNING/SUCCESS/FAILED/SKIPPED
  error         VARCHAR(255),                                     -- 失败原因(FAILED 时)
  items_json    JSONB,                                            -- 逐项求值结果快照(json,可复算)
  created_by    BIGINT       NOT NULL DEFAULT 0,                  -- 创建人ID,0=系统
  updated_by    BIGINT       NOT NULL DEFAULT 0,                  -- 更新人ID
  created_at    TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,  -- 创建时间(UTC)
  updated_at    TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,  -- 更新时间(UTC,应用层维护)
  deleted       SMALLINT     NOT NULL DEFAULT 0,                  -- 逻辑删除 0=否 1=是
  version       INTEGER      NOT NULL DEFAULT 0,                  -- 乐观锁版本号
  CONSTRAINT pk_cmp_calc_task PRIMARY KEY (id),
  CONSTRAINT uk_cmp_calc_task UNIQUE (run_id, emp_id)
);
CREATE INDEX idx_cmp_calc_task_run ON cmp_calc_task (tenant_id, run_id, status);

-- 薪资组
CREATE TABLE cmp_pay_group (
  id            BIGINT       NOT NULL,                            -- 主键: 雪花ID
  tenant_id     BIGINT       NOT NULL,                            -- 租户ID
  name          VARCHAR(64)  NOT NULL,                            -- 薪资组名称
  code          VARCHAR(64)  NOT NULL,                            -- 薪资组编码
  pay_cycle     VARCHAR(16)  NOT NULL DEFAULT 'MONTHLY',          -- 发薪周期 MONTHLY/BIWEEKLY
  created_by    BIGINT       NOT NULL DEFAULT 0,                  -- 创建人ID,0=系统
  updated_by    BIGINT       NOT NULL DEFAULT 0,                  -- 更新人ID
  created_at    TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,  -- 创建时间(UTC)
  updated_at    TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,  -- 更新时间(UTC,应用层维护)
  deleted       SMALLINT     NOT NULL DEFAULT 0,                  -- 逻辑删除 0=否 1=是
  version       INTEGER      NOT NULL DEFAULT 0,                  -- 乐观锁版本号
  CONSTRAINT pk_cmp_pay_group PRIMARY KEY (id),
  CONSTRAINT uk_cmp_pay_group_code UNIQUE (tenant_id, code)
);

-- 薪级
CREATE TABLE cmp_pay_grade (
  id            BIGINT         NOT NULL,                          -- 主键: 雪花ID
  tenant_id     BIGINT         NOT NULL,                          -- 租户ID
  name          VARCHAR(64)    NOT NULL,                          -- 薪级名称
  code          VARCHAR(64)    NOT NULL,                          -- 薪级编码
  min_salary    NUMERIC(18,4),                                    -- 下限
  max_salary    NUMERIC(18,4),                                    -- 上限
  created_by    BIGINT         NOT NULL DEFAULT 0,                -- 创建人ID,0=系统
  updated_by    BIGINT         NOT NULL DEFAULT 0,                -- 更新人ID
  created_at    TIMESTAMP(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP, -- 创建时间(UTC)
  updated_at    TIMESTAMP(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP, -- 更新时间(UTC,应用层维护)
  deleted       SMALLINT       NOT NULL DEFAULT 0,                -- 逻辑删除 0=否 1=是
  version       INTEGER        NOT NULL DEFAULT 0,                -- 乐观锁版本号
  CONSTRAINT pk_cmp_pay_grade PRIMARY KEY (id),
  CONSTRAINT uk_cmp_pay_grade_code UNIQUE (tenant_id, code)
);

-- 员工定薪(生效区间版本, 金额加密)
CREATE TABLE cmp_employee_pay (
  id                   BIGINT       NOT NULL,                     -- 主键: 雪花ID
  tenant_id            BIGINT       NOT NULL,                     -- 租户ID
  emp_id               BIGINT       NOT NULL,                     -- 员工ID(逻辑外键 emp_employee.id)
  pay_group_id         BIGINT       NOT NULL,                     -- 薪资组ID(逻辑外键 cmp_pay_group.id)
  pay_grade_id         BIGINT,                                    -- 薪级ID(逻辑外键 cmp_pay_grade.id)
  effective_from       DATE         NOT NULL,                     -- 生效开始日
  effective_to         DATE,                                      -- 生效结束日(NULL=现行)
  salary_amount_cipher VARCHAR(256),                              -- 定薪金额密文(逻辑列 salary_amount)
  salary_amount_mask   VARCHAR(64),                               -- 定薪金额掩码
  bank_account_cipher  VARCHAR(256),                              -- 银行账号密文(逻辑列 bank_account)
  bank_account_mask    VARCHAR(64),                               -- 银行账号掩码
  created_by           BIGINT       NOT NULL DEFAULT 0,           -- 创建人ID,0=系统
  updated_by           BIGINT       NOT NULL DEFAULT 0,           -- 更新人ID
  created_at           TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP, -- 创建时间(UTC)
  updated_at           TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP, -- 更新时间(UTC,应用层维护)
  deleted              SMALLINT     NOT NULL DEFAULT 0,           -- 逻辑删除 0=否 1=是
  version              INTEGER      NOT NULL DEFAULT 0,           -- 乐观锁版本号
  CONSTRAINT pk_cmp_employee_pay PRIMARY KEY (id),
  CONSTRAINT uk_cmp_employee_pay UNIQUE (tenant_id, emp_id, effective_from)
);

-- 社保公积金档案
CREATE TABLE cmp_social_fund_profile (
  id             BIGINT         NOT NULL,                         -- 主键: 雪花ID
  tenant_id      BIGINT         NOT NULL,                         -- 租户ID
  emp_id         BIGINT         NOT NULL,                         -- 员工ID(逻辑外键 emp_employee.id)
  effective_from DATE           NOT NULL,                         -- 生效开始日
  effective_to   DATE,                                            -- 生效结束日
  fund_type      VARCHAR(16)    NOT NULL,                         -- 类型 SOCIAL/HOUSING_FUND
  base_amount    NUMERIC(18,4)  NOT NULL DEFAULT 0,               -- 缴纳基数
  created_by     BIGINT         NOT NULL DEFAULT 0,               -- 创建人ID,0=系统
  updated_by     BIGINT         NOT NULL DEFAULT 0,               -- 更新人ID
  created_at     TIMESTAMP(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP, -- 创建时间(UTC)
  updated_at     TIMESTAMP(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP, -- 更新时间(UTC,应用层维护)
  deleted        SMALLINT       NOT NULL DEFAULT 0,               -- 逻辑删除 0=否 1=是
  version        INTEGER        NOT NULL DEFAULT 0,               -- 乐观锁版本号
  CONSTRAINT pk_cmp_social_fund_profile PRIMARY KEY (id),
  CONSTRAINT uk_cmp_social_fund UNIQUE (tenant_id, emp_id, fund_type, effective_from)
);

-- 专项附加扣除(加密)
CREATE TABLE cmp_tax_deduction (
  id                      BIGINT       NOT NULL,                  -- 主键: 雪花ID
  tenant_id               BIGINT       NOT NULL,                  -- 租户ID
  emp_id                  BIGINT       NOT NULL,                  -- 员工ID(逻辑外键 emp_employee.id)
  effective_from          DATE         NOT NULL,                  -- 生效开始日
  effective_to            DATE,                                   -- 生效结束日
  deduction_amount_cipher VARCHAR(256),                           -- 专项附加扣除金额密文(逻辑列 deduction_amount)
  deduction_amount_mask   VARCHAR(64),                            -- 专项附加扣除金额掩码
  created_by              BIGINT       NOT NULL DEFAULT 0,        -- 创建人ID,0=系统
  updated_by              BIGINT       NOT NULL DEFAULT 0,        -- 更新人ID
  created_at              TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP, -- 创建时间(UTC)
  updated_at              TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP, -- 更新时间(UTC,应用层维护)
  deleted                 SMALLINT     NOT NULL DEFAULT 0,        -- 逻辑删除 0=否 1=是
  version                 INTEGER      NOT NULL DEFAULT 0,        -- 乐观锁版本号
  CONSTRAINT pk_cmp_tax_deduction PRIMARY KEY (id),
  CONSTRAINT uk_cmp_tax_deduction UNIQUE (tenant_id, emp_id, effective_from)
);

-- 算薪批次(状态机)
CREATE TABLE cmp_payroll_run (
  id            BIGINT       NOT NULL,                            -- 主键: 雪花ID
  tenant_id     BIGINT       NOT NULL,                            -- 租户ID
  period_code   VARCHAR(16)  NOT NULL,                            -- 周期编码(如 2026-01)
  pay_group_id  BIGINT       NOT NULL,                            -- 薪资组ID(逻辑外键 cmp_pay_group.id)
  status        VARCHAR(16)  NOT NULL DEFAULT 'INIT',             -- 状态 INIT/CALCULATING/TRIAL/CALC/REVIEW/APPROVE/PAID/LOCK
  calc_by       BIGINT,                                           -- 计算人ID
  review_by     BIGINT,                                           -- 复核人ID
  reviewed_by_1 BIGINT,                                           -- 第一复核人ID(双人复核)
  reviewed_at_1 TIMESTAMP(3),                                     -- 第一复核时间
  reviewed_by_2 BIGINT,                                           -- 第二复核人ID(须与第一复核人不同)
  reviewed_at_2 TIMESTAMP(3),                                     -- 第二复核时间
  review_comment VARCHAR(255),                                    -- 最近一次复核意见/驳回理由
  created_by    BIGINT       NOT NULL DEFAULT 0,                  -- 创建人ID,0=系统
  updated_by    BIGINT       NOT NULL DEFAULT 0,                  -- 更新人ID
  created_at    TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,  -- 创建时间(UTC)
  updated_at    TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,  -- 更新时间(UTC,应用层维护)
  deleted       SMALLINT     NOT NULL DEFAULT 0,                  -- 逻辑删除 0=否 1=是
  version       INTEGER      NOT NULL DEFAULT 0,                  -- 乐观锁版本号
  CONSTRAINT pk_cmp_payroll_run PRIMARY KEY (id),
  CONSTRAINT uk_cmp_run_period_group UNIQUE (tenant_id, period_code, pay_group_id)
);

-- 算薪明细(run+emp, 输入快照 json, 金额加密)
CREATE TABLE cmp_payroll_detail (
  id                BIGINT       NOT NULL,                        -- 主键: 雪花ID
  tenant_id         BIGINT       NOT NULL,                        -- 租户ID
  run_id            BIGINT       NOT NULL,                        -- 算薪批次ID(逻辑外键 cmp_payroll_run.id)
  emp_id            BIGINT       NOT NULL,                        -- 员工ID(逻辑外键 emp_employee.id)
  input_snapshot    JSONB,                                        -- 算薪输入快照(json,可复算)
  amount_cipher     VARCHAR(256),                                 -- 应发金额密文(逻辑列 amount)
  amount_mask       VARCHAR(64),                                  -- 应发金额掩码
  net_amount_cipher VARCHAR(256),                                 -- 实发金额密文(逻辑列 net_amount)
  net_amount_mask   VARCHAR(64),                                  -- 实发金额掩码
  created_by        BIGINT       NOT NULL DEFAULT 0,              -- 创建人ID,0=系统
  updated_by        BIGINT       NOT NULL DEFAULT 0,              -- 更新人ID
  created_at        TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP, -- 创建时间(UTC)
  updated_at        TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP, -- 更新时间(UTC,应用层维护)
  deleted           SMALLINT     NOT NULL DEFAULT 0,              -- 逻辑删除 0=否 1=是
  version           INTEGER      NOT NULL DEFAULT 0,              -- 乐观锁版本号
  CONSTRAINT pk_cmp_payroll_detail PRIMARY KEY (id),
  CONSTRAINT uk_cmp_detail_run_emp UNIQUE (tenant_id, run_id, emp_id)
);
CREATE INDEX idx_cmp_detail_run ON cmp_payroll_detail (tenant_id, run_id);

-- 算薪调整
CREATE TABLE cmp_payroll_adjustment (
  id            BIGINT         NOT NULL,                          -- 主键: 雪花ID
  tenant_id     BIGINT         NOT NULL,                          -- 租户ID
  run_id        BIGINT         NOT NULL,                          -- 算薪批次ID(逻辑外键 cmp_payroll_run.id)
  emp_id        BIGINT         NOT NULL,                          -- 员工ID(逻辑外键 emp_employee.id)
  adjust_type   VARCHAR(16)    NOT NULL,                          -- 调整类型
  amount        NUMERIC(18,4)  NOT NULL DEFAULT 0,                -- 调整金额
  reason        VARCHAR(255),                                     -- 调整原因
  created_by    BIGINT         NOT NULL DEFAULT 0,                -- 创建人ID,0=系统
  updated_by    BIGINT         NOT NULL DEFAULT 0,                -- 更新人ID
  created_at    TIMESTAMP(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP, -- 创建时间(UTC)
  updated_at    TIMESTAMP(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP, -- 更新时间(UTC,应用层维护)
  deleted       SMALLINT       NOT NULL DEFAULT 0,                -- 逻辑删除 0=否 1=是
  version       INTEGER        NOT NULL DEFAULT 0,                -- 乐观锁版本号
  CONSTRAINT pk_cmp_payroll_adjustment PRIMARY KEY (id)
);
CREATE INDEX idx_cmp_adjustment ON cmp_payroll_adjustment (tenant_id, run_id, emp_id);

-- 发放指令
CREATE TABLE cmp_payment_instruction (
  id            BIGINT       NOT NULL,                            -- 主键: 雪花ID
  tenant_id     BIGINT       NOT NULL,                            -- 租户ID
  run_id        BIGINT       NOT NULL,                            -- 算薪批次ID(逻辑外键 cmp_payroll_run.id)
  emp_id        BIGINT       NOT NULL,                            -- 员工ID(逻辑外键 emp_employee.id)
  pay_status    VARCHAR(16)  NOT NULL DEFAULT 'PENDING',          -- 发放状态 PENDING/SUCCESS/FAILED
  paid_at       TIMESTAMP(3),                                     -- 发放时间(UTC)
  created_by    BIGINT       NOT NULL DEFAULT 0,                  -- 创建人ID,0=系统
  updated_by    BIGINT       NOT NULL DEFAULT 0,                  -- 更新人ID
  created_at    TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,  -- 创建时间(UTC)
  updated_at    TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,  -- 更新时间(UTC,应用层维护)
  deleted       SMALLINT     NOT NULL DEFAULT 0,                  -- 逻辑删除 0=否 1=是
  version       INTEGER      NOT NULL DEFAULT 0,                  -- 乐观锁版本号
  CONSTRAINT pk_cmp_payment_instruction PRIMARY KEY (id),
  CONSTRAINT uk_cmp_payment_run_emp UNIQUE (tenant_id, run_id, emp_id)
);

-- 工资条(密文 + 发放状态)
CREATE TABLE cmp_payslip (
  id            BIGINT       NOT NULL,                            -- 主键: 雪花ID
  tenant_id     BIGINT       NOT NULL,                            -- 租户ID
  run_id        BIGINT       NOT NULL,                            -- 算薪批次ID(逻辑外键 cmp_payroll_run.id)
  emp_id        BIGINT       NOT NULL,                            -- 员工ID(逻辑外键 emp_employee.id)
  net_pay_cipher VARCHAR(256),                                    -- 实发净额密文(逻辑列 net_pay)
  net_pay_mask  VARCHAR(64),                                      -- 实发净额掩码
  send_status   VARCHAR(16)  NOT NULL DEFAULT 'PENDING',          -- 发放状态 PENDING/SENT/VIEWED
  sent_at       TIMESTAMP(3),                                     -- 发送时间(UTC)
  created_by    BIGINT       NOT NULL DEFAULT 0,                  -- 创建人ID,0=系统
  updated_by    BIGINT       NOT NULL DEFAULT 0,                  -- 更新人ID
  created_at    TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,  -- 创建时间(UTC)
  updated_at    TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,  -- 更新时间(UTC,应用层维护)
  deleted       SMALLINT     NOT NULL DEFAULT 0,                  -- 逻辑删除 0=否 1=是
  version       INTEGER      NOT NULL DEFAULT 0,                  -- 乐观锁版本号
  CONSTRAINT pk_cmp_payslip PRIMARY KEY (id),
  CONSTRAINT uk_cmp_payslip_run_emp UNIQUE (tenant_id, run_id, emp_id)
);

-- 导入批次
CREATE TABLE cmp_import_batch (
  id            BIGINT       NOT NULL,                            -- 主键: 雪花ID
  tenant_id     BIGINT       NOT NULL,                            -- 租户ID
  batch_no      VARCHAR(64)  NOT NULL,                            -- 批次号
  import_type   VARCHAR(16)  NOT NULL,                            -- 导入类型
  total_rows    INTEGER      NOT NULL DEFAULT 0,                  -- 总行数
  success_rows  INTEGER      NOT NULL DEFAULT 0,                  -- 成功行数
  fail_rows     INTEGER      NOT NULL DEFAULT 0,                  -- 失败行数
  status        VARCHAR(16)  NOT NULL DEFAULT 'RUNNING',          -- 状态 RUNNING/DONE/FAILED
  created_by    BIGINT       NOT NULL DEFAULT 0,                  -- 创建人ID,0=系统
  updated_by    BIGINT       NOT NULL DEFAULT 0,                  -- 更新人ID
  created_at    TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,  -- 创建时间(UTC)
  updated_at    TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,  -- 更新时间(UTC,应用层维护)
  deleted       SMALLINT     NOT NULL DEFAULT 0,                  -- 逻辑删除 0=否 1=是
  version       INTEGER      NOT NULL DEFAULT 0,                  -- 乐观锁版本号
  CONSTRAINT pk_cmp_import_batch PRIMARY KEY (id),
  CONSTRAINT uk_cmp_import_batch UNIQUE (tenant_id, batch_no)
);

-- 汇率(CMP-F-023 多币种底座)
CREATE TABLE cmp_exchange_rate (
  id             NUMERIC(18,8) NOT NULL,                           -- 主键: 雪花ID
  tenant_id      BIGINT        NOT NULL,                           -- 租户ID
  from_currency  CHAR(3)       NOT NULL,                           -- 源币种(ISO 4217)
  to_currency    CHAR(3)       NOT NULL,                           -- 目标币种(ISO 4217)
  rate           NUMERIC(18,8) NOT NULL,                           -- 汇率: 1 from = rate to
  effective_date DATE          NOT NULL,                           -- 生效日期
  created_by     BIGINT        NOT NULL DEFAULT 0,                 -- 创建人ID,0=系统
  updated_by     BIGINT        NOT NULL DEFAULT 0,                 -- 更新人ID
  created_at     TIMESTAMP(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP, -- 创建时间(UTC)
  updated_at     TIMESTAMP(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP, -- 更新时间(UTC,应用层维护)
  deleted        SMALLINT      NOT NULL DEFAULT 0,                 -- 逻辑删除 0=否 1=是
  version        INTEGER       NOT NULL DEFAULT 0,                 -- 乐观锁版本号
  CONSTRAINT pk_cmp_exchange_rate PRIMARY KEY (id),
  CONSTRAINT uk_cmp_exchange_rate UNIQUE (tenant_id, from_currency, to_currency, effective_date)
);

-- 期间关账记录(CMP-F-020, 历史多锁并存, ACTIVE 至多一条由应用层保证)
CREATE TABLE cmp_period_lock (
  id             BIGINT       NOT NULL,                         -- 主键: 雪花ID
  tenant_id      BIGINT       NOT NULL,                         -- 租户ID
  period_code    VARCHAR(16)  NOT NULL,                         -- 周期编码(如 2026-01)
  status         VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE',        -- 状态 ACTIVE/RELEASED
  locked_by      BIGINT       NOT NULL,                         -- 关账人ID
  locked_at      TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP, -- 关账时间(UTC)
  released_by    BIGINT,                                        -- 解锁人ID(NULL=未解锁)
  released_at    TIMESTAMP(3),                                  -- 解锁时间(UTC,NULL=未解锁)
  unlock_reason  VARCHAR(255),                                  -- 解锁理由(NULL=未解锁)
  created_by     BIGINT       NOT NULL DEFAULT 0,               -- 创建人ID,0=系统
  updated_by     BIGINT       NOT NULL DEFAULT 0,               -- 更新人ID
  created_at     TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP, -- 创建时间(UTC)
  updated_at     TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP, -- 更新时间(UTC,应用层维护)
  deleted        SMALLINT     NOT NULL DEFAULT 0,               -- 逻辑删除 0=否 1=是
  version        INTEGER      NOT NULL DEFAULT 0,               -- 乐观锁版本号
  CONSTRAINT pk_cmp_period_lock PRIMARY KEY (id),
  CONSTRAINT uk_cmp_period_lock UNIQUE (tenant_id, period_code, locked_at)
);
CREATE INDEX idx_cmp_period_lock_status ON cmp_period_lock (tenant_id, period_code, status);

-- =====================================================================
-- 域 7: prf_db 绩效与培训
-- =====================================================================
CREATE SCHEMA IF NOT EXISTS prf_db;
SET search_path TO prf_db;

-- 考核方案
CREATE TABLE prf_plan (
  id            BIGINT       NOT NULL,                            -- 主键: 雪花ID
  tenant_id     BIGINT       NOT NULL,                            -- 租户ID
  name          VARCHAR(64)  NOT NULL,                            -- 方案名称
  code          VARCHAR(64)  NOT NULL,                            -- 方案编码
  status        SMALLINT     NOT NULL DEFAULT 1,                  -- 状态 1=启用 0=停用
  created_by    BIGINT       NOT NULL DEFAULT 0,                  -- 创建人ID,0=系统
  updated_by    BIGINT       NOT NULL DEFAULT 0,                  -- 更新人ID
  created_at    TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,  -- 创建时间(UTC)
  updated_at    TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,  -- 更新时间(UTC,应用层维护)
  deleted       SMALLINT     NOT NULL DEFAULT 0,                  -- 逻辑删除 0=否 1=是
  version       INTEGER      NOT NULL DEFAULT 0,                  -- 乐观锁版本号
  CONSTRAINT pk_prf_plan PRIMARY KEY (id),
  CONSTRAINT uk_prf_plan_code UNIQUE (tenant_id, code)
);

-- 考核周期
CREATE TABLE prf_cycle (
  id            BIGINT       NOT NULL,                            -- 主键: 雪花ID
  tenant_id     BIGINT       NOT NULL,                            -- 租户ID
  plan_id       BIGINT       NOT NULL,                            -- 方案ID(逻辑外键 prf_plan.id)
  period_code   VARCHAR(16)  NOT NULL,                            -- 周期编码(如 2026H1)
  start_date    DATE         NOT NULL,                            -- 周期开始日
  end_date      DATE         NOT NULL,                            -- 周期结束日
  status        VARCHAR(16)  NOT NULL DEFAULT 'DRAFT',            -- 状态 DRAFT/ACTIVE/SCORING/CLOSED（仅白名单流转，见 CycleStateMachine）
  created_by    BIGINT       NOT NULL DEFAULT 0,                  -- 创建人ID,0=系统
  updated_by    BIGINT       NOT NULL DEFAULT 0,                  -- 更新人ID
  created_at    TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,  -- 创建时间(UTC)
  updated_at    TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,  -- 更新时间(UTC,应用层维护)
  deleted       SMALLINT     NOT NULL DEFAULT 0,                  -- 逻辑删除 0=否 1=是
  version       INTEGER      NOT NULL DEFAULT 0,                  -- 乐观锁版本号
  CONSTRAINT pk_prf_cycle PRIMARY KEY (id),
  CONSTRAINT uk_prf_cycle UNIQUE (tenant_id, plan_id, period_code)
);

-- 目标(KPI 权重)
CREATE TABLE prf_goal (
  id            BIGINT       NOT NULL,                            -- 主键: 雪花ID
  tenant_id     BIGINT       NOT NULL,                            -- 租户ID
  cycle_id      BIGINT       NOT NULL,                            -- 周期ID(逻辑外键 prf_cycle.id)
  emp_id        BIGINT       NOT NULL,                            -- 员工ID(逻辑外键 emp_employee.id)
  title         VARCHAR(128) NOT NULL,                            -- 目标标题
  dimension     VARCHAR(16)  NOT NULL DEFAULT 'KPI',              -- 目标维度 KPI/OKR/COMPETENCY（权重合计口径：同周期+同员工+同维度=100）
  weight        NUMERIC(5,2) NOT NULL DEFAULT 0,                  -- 权重(%)
  target_value  VARCHAR(255),                                     -- 目标值
  result_value  VARCHAR(255),                                     -- 完成值
  created_by    BIGINT       NOT NULL DEFAULT 0,                  -- 创建人ID,0=系统
  updated_by    BIGINT       NOT NULL DEFAULT 0,                  -- 更新人ID
  created_at    TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,  -- 创建时间(UTC)
  updated_at    TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,  -- 更新时间(UTC,应用层维护)
  deleted       SMALLINT     NOT NULL DEFAULT 0,                  -- 逻辑删除 0=否 1=是
  version       INTEGER      NOT NULL DEFAULT 0,                  -- 乐观锁版本号
  CONSTRAINT pk_prf_goal PRIMARY KEY (id)
);
CREATE INDEX idx_prf_goal ON prf_goal (tenant_id, cycle_id, emp_id, dimension);

-- OKR 目标
CREATE TABLE prf_okr_objective (
  id            BIGINT       NOT NULL,                            -- 主键: 雪花ID
  tenant_id     BIGINT       NOT NULL,                            -- 租户ID
  cycle_id      BIGINT       NOT NULL,                            -- 周期ID(逻辑外键 prf_cycle.id)
  emp_id        BIGINT       NOT NULL,                            -- 员工ID(逻辑外键 emp_employee.id)
  objective     VARCHAR(255) NOT NULL,                            -- 目标O
  key_results   JSONB,                                            -- 关键结果KR列表(json)
  created_by    BIGINT       NOT NULL DEFAULT 0,                  -- 创建人ID,0=系统
  updated_by    BIGINT       NOT NULL DEFAULT 0,                  -- 更新人ID
  created_at    TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,  -- 创建时间(UTC)
  updated_at    TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,  -- 更新时间(UTC,应用层维护)
  deleted       SMALLINT     NOT NULL DEFAULT 0,                  -- 逻辑删除 0=否 1=是
  version       INTEGER      NOT NULL DEFAULT 0,                  -- 乐观锁版本号
  CONSTRAINT pk_prf_okr_objective PRIMARY KEY (id)
);
CREATE INDEX idx_prf_okr ON prf_okr_objective (tenant_id, cycle_id, emp_id);

-- 评估任务
CREATE TABLE prf_eval_task (
  id             BIGINT       NOT NULL,                           -- 主键: 雪花ID
  tenant_id      BIGINT       NOT NULL,                           -- 租户ID
  cycle_id       BIGINT       NOT NULL,                           -- 周期ID(逻辑外键 prf_cycle.id)
  emp_id         BIGINT       NOT NULL,                           -- 被评估人员工ID(逻辑外键 emp_employee.id)
  evaluator_role VARCHAR(16)  NOT NULL,                           -- 评价人角色 SELF/SUPERVISOR/PEER/SUBORDINATE
  status         VARCHAR(16)  NOT NULL DEFAULT 'PENDING',         -- 状态 PENDING/DONE
  created_by     BIGINT       NOT NULL DEFAULT 0,                 -- 创建人ID,0=系统
  updated_by     BIGINT       NOT NULL DEFAULT 0,                 -- 更新人ID
  created_at     TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP, -- 创建时间(UTC)
  updated_at     TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP, -- 更新时间(UTC,应用层维护)
  deleted        SMALLINT     NOT NULL DEFAULT 0,                 -- 逻辑删除 0=否 1=是
  version        INTEGER      NOT NULL DEFAULT 0,                 -- 乐观锁版本号
  CONSTRAINT pk_prf_eval_task PRIMARY KEY (id),
  CONSTRAINT uk_prf_eval_task UNIQUE (tenant_id, cycle_id, emp_id, evaluator_role)
);

-- 评价(评价人环 json)
CREATE TABLE prf_evaluation (
  id            BIGINT       NOT NULL,                            -- 主键: 雪花ID
  tenant_id     BIGINT       NOT NULL,                            -- 租户ID
  task_id       BIGINT       NOT NULL,                            -- 评估任务ID(逻辑外键 prf_eval_task.id)
  evaluator_id  BIGINT       NOT NULL,                            -- 评价人用户ID(逻辑外键 auth_user.id)
  scores        JSONB,                                            -- 评分明细(json)
  comment       VARCHAR(512),                                     -- 评语
  created_by    BIGINT       NOT NULL DEFAULT 0,                  -- 创建人ID,0=系统
  updated_by    BIGINT       NOT NULL DEFAULT 0,                  -- 更新人ID
  created_at    TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,  -- 创建时间(UTC)
  updated_at    TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,  -- 更新时间(UTC,应用层维护)
  deleted       SMALLINT     NOT NULL DEFAULT 0,                  -- 逻辑删除 0=否 1=是
  version       INTEGER      NOT NULL DEFAULT 0,                  -- 乐观锁版本号
  CONSTRAINT pk_prf_evaluation PRIMARY KEY (id),
  CONSTRAINT uk_prf_evaluation UNIQUE (tenant_id, task_id, evaluator_id)
);

-- 强制分布规则
CREATE TABLE prf_forced_rule (
  id            BIGINT       NOT NULL,                            -- 主键: 雪花ID
  tenant_id     BIGINT       NOT NULL,                            -- 租户ID
  plan_id       BIGINT       NOT NULL,                            -- 方案ID(逻辑外键 prf_plan.id)
  grade         VARCHAR(16)  NOT NULL,                            -- 等级(如 A/B/C/D)
  ratio         NUMERIC(5,2) NOT NULL,                            -- 比例(%)
  created_by    BIGINT       NOT NULL DEFAULT 0,                  -- 创建人ID,0=系统
  updated_by    BIGINT       NOT NULL DEFAULT 0,                  -- 更新人ID
  created_at    TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,  -- 创建时间(UTC)
  updated_at    TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,  -- 更新时间(UTC,应用层维护)
  deleted       SMALLINT     NOT NULL DEFAULT 0,                  -- 逻辑删除 0=否 1=是
  version       INTEGER      NOT NULL DEFAULT 0,                  -- 乐观锁版本号
  CONSTRAINT pk_prf_forced_rule PRIMARY KEY (id),
  CONSTRAINT uk_prf_forced_rule UNIQUE (tenant_id, plan_id, grade)
);

-- 校准会议
CREATE TABLE prf_calibration_session (
  id            BIGINT       NOT NULL,                            -- 主键: 雪花ID
  tenant_id     BIGINT       NOT NULL,                            -- 租户ID
  cycle_id      BIGINT       NOT NULL,                            -- 周期ID(逻辑外键 prf_cycle.id)
  dept_id       BIGINT       NOT NULL,                            -- 部门ID(逻辑外键 org_dept.id)
  status        VARCHAR(16)  NOT NULL DEFAULT 'DRAFT',            -- 状态 DRAFT/IN_PROGRESS/DONE
  created_by    BIGINT       NOT NULL DEFAULT 0,                  -- 创建人ID,0=系统
  updated_by    BIGINT       NOT NULL DEFAULT 0,                  -- 更新人ID
  created_at    TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,  -- 创建时间(UTC)
  updated_at    TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,  -- 更新时间(UTC,应用层维护)
  deleted       SMALLINT     NOT NULL DEFAULT 0,                  -- 逻辑删除 0=否 1=是
  version       INTEGER      NOT NULL DEFAULT 0,                  -- 乐观锁版本号
  CONSTRAINT pk_prf_calibration_session PRIMARY KEY (id),
  CONSTRAINT uk_prf_calibration UNIQUE (tenant_id, cycle_id, dept_id)
);

-- 校准快照
CREATE TABLE prf_calibration_snapshot (
  id            BIGINT       NOT NULL,                            -- 主键: 雪花ID
  tenant_id     BIGINT       NOT NULL,                            -- 租户ID
  session_id    BIGINT       NOT NULL,                            -- 校准会议ID(逻辑外键 prf_calibration_session.id)
  snapshot      JSONB        NOT NULL,                            -- 校准快照(九宫格/调整结果)
  created_by    BIGINT       NOT NULL DEFAULT 0,                  -- 创建人ID,0=系统
  updated_by    BIGINT       NOT NULL DEFAULT 0,                  -- 更新人ID
  created_at    TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,  -- 创建时间(UTC)
  updated_at    TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,  -- 更新时间(UTC,应用层维护)
  deleted       SMALLINT     NOT NULL DEFAULT 0,                  -- 逻辑删除 0=否 1=是
  version       INTEGER      NOT NULL DEFAULT 0,                  -- 乐观锁版本号
  CONSTRAINT pk_prf_calibration_snapshot PRIMARY KEY (id)
);
CREATE INDEX idx_prf_calib_snapshot ON prf_calibration_snapshot (tenant_id, session_id);

-- 申诉(关联 flow)
CREATE TABLE prf_appeal (
  id            BIGINT       NOT NULL,                            -- 主键: 雪花ID
  tenant_id     BIGINT       NOT NULL,                            -- 租户ID
  emp_id        BIGINT       NOT NULL,                            -- 员工ID(逻辑外键 emp_employee.id)
  cycle_id      BIGINT       NOT NULL,                            -- 周期ID(逻辑外键 prf_cycle.id)
  biz_key       VARCHAR(64)  NOT NULL,                            -- 业务单号(流程幂等键)
  reason        VARCHAR(255),                                     -- 申诉理由
  status        VARCHAR(16)  NOT NULL DEFAULT 'DRAFT',            -- 状态 DRAFT/PROCESSING/APPROVED/REJECTED
  proc_inst_id  VARCHAR(64),                                      -- 流程实例ID
  created_by    BIGINT       NOT NULL DEFAULT 0,                  -- 创建人ID,0=系统
  updated_by    BIGINT       NOT NULL DEFAULT 0,                  -- 更新人ID
  created_at    TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,  -- 创建时间(UTC)
  updated_at    TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,  -- 更新时间(UTC,应用层维护)
  deleted       SMALLINT     NOT NULL DEFAULT 0,                  -- 逻辑删除 0=否 1=是
  version       INTEGER      NOT NULL DEFAULT 0,                  -- 乐观锁版本号
  CONSTRAINT pk_prf_appeal PRIMARY KEY (id),
  CONSTRAINT uk_prf_appeal_biz UNIQUE (tenant_id, biz_key)
);

-- 绩效面谈(含 PIP 跟踪)
CREATE TABLE prf_interview (
  id               BIGINT        NOT NULL,                            -- 主键: 雪花ID
  tenant_id        BIGINT        NOT NULL,                            -- 租户ID
  cycle_id         BIGINT        NOT NULL,                            -- 周期ID(逻辑外键 prf_cycle.id)
  emp_id           BIGINT        NOT NULL,                            -- 员工ID(逻辑外键 emp_employee.id)
  round            INTEGER       NOT NULL DEFAULT 1,                  -- 面谈轮次(同周期同员工递增)
  interview_date   DATE          NOT NULL,                            -- 面谈日期
  interviewer_emp_id BIGINT      NOT NULL,                            -- 面谈人员工ID(逻辑外键 emp_employee.id)
  summary          VARCHAR(1000),                                     -- 面谈纪要
  conclusion       VARCHAR(16)   NOT NULL,                            -- 结论 IMPROVE改进/KEEP维持/PROMOTE晋升建议/PIP绩效改善计划
  pip_start_date   DATE,                                              -- PIP开始日(conclusion=PIP 时必填)
  pip_end_date     DATE,                                              -- PIP结束日(conclusion=PIP 时必填且大于开始日)
  pip_status       VARCHAR(16),                                       -- PIP状态 ONGOING/PASSED/FAILED(仅 conclusion=PIP)
  created_by       BIGINT        NOT NULL DEFAULT 0,                  -- 创建人ID,0=系统
  updated_by       BIGINT        NOT NULL DEFAULT 0,                  -- 更新人ID
  created_at       TIMESTAMP(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP,  -- 创建时间(UTC)
  updated_at       TIMESTAMP(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP,  -- 更新时间(UTC,应用层维护)
  deleted          SMALLINT      NOT NULL DEFAULT 0,                  -- 逻辑删除 0=否 1=是
  version          INTEGER       NOT NULL DEFAULT 0,                  -- 乐观锁版本号
  CONSTRAINT pk_prf_interview PRIMARY KEY (id),
  CONSTRAINT uk_prf_interview UNIQUE (tenant_id, cycle_id, emp_id, round)
);

CREATE INDEX idx_prf_interview_emp ON prf_interview (tenant_id, emp_id);

-- 培训计划
CREATE TABLE prf_train_plan (
  id            BIGINT       NOT NULL,                            -- 主键: 雪花ID
  tenant_id     BIGINT       NOT NULL,                            -- 租户ID
  name          VARCHAR(128) NOT NULL,                            -- 计划名称
  code          VARCHAR(64)  NOT NULL,                            -- 计划编码
  start_date    DATE,                                             -- 计划开始日
  end_date      DATE,                                             -- 计划结束日
  created_by    BIGINT       NOT NULL DEFAULT 0,                  -- 创建人ID,0=系统
  updated_by    BIGINT       NOT NULL DEFAULT 0,                  -- 更新人ID
  created_at    TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,  -- 创建时间(UTC)
  updated_at    TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,  -- 更新时间(UTC,应用层维护)
  deleted       SMALLINT     NOT NULL DEFAULT 0,                  -- 逻辑删除 0=否 1=是
  version       INTEGER      NOT NULL DEFAULT 0,                  -- 乐观锁版本号
  CONSTRAINT pk_prf_train_plan PRIMARY KEY (id),
  CONSTRAINT uk_prf_train_plan_code UNIQUE (tenant_id, code)
);

-- 课程
CREATE TABLE prf_course (
  id            BIGINT       NOT NULL,                            -- 主键: 雪花ID
  tenant_id     BIGINT       NOT NULL,                            -- 租户ID
  name          VARCHAR(128) NOT NULL,                            -- 课程名称
  code          VARCHAR(64)  NOT NULL,                            -- 课程编码
  hours         NUMERIC(6,2) NOT NULL DEFAULT 0,                  -- 课程学时
  created_by    BIGINT       NOT NULL DEFAULT 0,                  -- 创建人ID,0=系统
  updated_by    BIGINT       NOT NULL DEFAULT 0,                  -- 更新人ID
  created_at    TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,  -- 创建时间(UTC)
  updated_at    TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,  -- 更新时间(UTC,应用层维护)
  deleted       SMALLINT     NOT NULL DEFAULT 0,                  -- 逻辑删除 0=否 1=是
  version       INTEGER      NOT NULL DEFAULT 0,                  -- 乐观锁版本号
  CONSTRAINT pk_prf_course PRIMARY KEY (id),
  CONSTRAINT uk_prf_course_code UNIQUE (tenant_id, code)
);

-- 讲师
CREATE TABLE prf_instructor (
  id            BIGINT       NOT NULL,                            -- 主键: 雪花ID
  tenant_id     BIGINT       NOT NULL,                            -- 租户ID
  emp_id        BIGINT       NOT NULL,                            -- 讲师员工ID(逻辑外键 emp_employee.id)
  created_by    BIGINT       NOT NULL DEFAULT 0,                  -- 创建人ID,0=系统
  updated_by    BIGINT       NOT NULL DEFAULT 0,                  -- 更新人ID
  created_at    TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,  -- 创建时间(UTC)
  updated_at    TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,  -- 更新时间(UTC,应用层维护)
  deleted       SMALLINT     NOT NULL DEFAULT 0,                  -- 逻辑删除 0=否 1=是
  version       INTEGER      NOT NULL DEFAULT 0,                  -- 乐观锁版本号
  CONSTRAINT pk_prf_instructor PRIMARY KEY (id),
  CONSTRAINT uk_prf_instructor UNIQUE (tenant_id, emp_id)
);

-- 开班场次
CREATE TABLE prf_class_session (
  id            BIGINT       NOT NULL,                            -- 主键: 雪花ID
  tenant_id     BIGINT       NOT NULL,                            -- 租户ID
  course_id     BIGINT       NOT NULL,                            -- 课程ID(逻辑外键 prf_course.id)
  instructor_id BIGINT,                                           -- 讲师ID(逻辑外键 prf_instructor.id)
  start_at      TIMESTAMP(3),                                     -- 开课时间(UTC)
  capacity      INTEGER      NOT NULL DEFAULT 0,                  -- 容量
  created_by    BIGINT       NOT NULL DEFAULT 0,                  -- 创建人ID,0=系统
  updated_by    BIGINT       NOT NULL DEFAULT 0,                  -- 更新人ID
  created_at    TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,  -- 创建时间(UTC)
  updated_at    TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,  -- 更新时间(UTC,应用层维护)
  deleted       SMALLINT     NOT NULL DEFAULT 0,                  -- 逻辑删除 0=否 1=是
  version       INTEGER      NOT NULL DEFAULT 0,                  -- 乐观锁版本号
  CONSTRAINT pk_prf_class_session PRIMARY KEY (id)
);
CREATE INDEX idx_prf_class_session ON prf_class_session (tenant_id, course_id, start_at);

-- 报名/参训
CREATE TABLE prf_enrollment (
  id            BIGINT       NOT NULL,                            -- 主键: 雪花ID
  tenant_id     BIGINT       NOT NULL,                            -- 租户ID
  session_id    BIGINT       NOT NULL,                            -- 场次ID(逻辑外键 prf_class_session.id)
  emp_id        BIGINT       NOT NULL,                            -- 员工ID(逻辑外键 emp_employee.id)
  status        VARCHAR(16)  NOT NULL DEFAULT 'ENROLLED',         -- 状态 ENROLLED/COMPLETED/CANCELLED
  created_by    BIGINT       NOT NULL DEFAULT 0,                  -- 创建人ID,0=系统
  updated_by    BIGINT       NOT NULL DEFAULT 0,                  -- 更新人ID
  created_at    TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,  -- 创建时间(UTC)
  updated_at    TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,  -- 更新时间(UTC,应用层维护)
  deleted       SMALLINT     NOT NULL DEFAULT 0,                  -- 逻辑删除 0=否 1=是
  version       INTEGER      NOT NULL DEFAULT 0,                  -- 乐观锁版本号
  CONSTRAINT pk_prf_enrollment PRIMARY KEY (id),
  CONSTRAINT uk_prf_enrollment UNIQUE (tenant_id, session_id, emp_id)
);

-- 学时台账
CREATE TABLE prf_credit_ledger (
  id            BIGINT       NOT NULL,                            -- 主键: 雪花ID
  tenant_id     BIGINT       NOT NULL,                            -- 租户ID
  emp_id        BIGINT       NOT NULL,                            -- 员工ID(逻辑外键 emp_employee.id)
  course_id     BIGINT,                                           -- 课程ID(逻辑外键 prf_course.id)
  credits       NUMERIC(6,2) NOT NULL DEFAULT 0,                  -- 获得学时
  created_by    BIGINT       NOT NULL DEFAULT 0,                  -- 创建人ID,0=系统
  updated_by    BIGINT       NOT NULL DEFAULT 0,                  -- 更新人ID
  created_at    TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,  -- 创建时间(UTC)
  updated_at    TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,  -- 更新时间(UTC,应用层维护)
  deleted       SMALLINT     NOT NULL DEFAULT 0,                  -- 逻辑删除 0=否 1=是
  version       INTEGER      NOT NULL DEFAULT 0,                  -- 乐观锁版本号
  CONSTRAINT pk_prf_credit_ledger PRIMARY KEY (id)
);
CREATE INDEX idx_prf_credit_ledger ON prf_credit_ledger (tenant_id, emp_id, created_at);

-- =====================================================================
-- 域 8: flow_db 流程中台(仅 flow_ext_* 扩展表; ACT_* 由 Flowable 引擎自动建)
-- =====================================================================
CREATE SCHEMA IF NOT EXISTS flow_db;
SET search_path TO flow_db;

-- 业务单据-流程实例映射(发起幂等锁)
CREATE TABLE flow_ext_biz_relation (
  id            BIGINT       NOT NULL,                            -- 主键: 雪花ID
  tenant_id     BIGINT       NOT NULL,                            -- 租户ID
  proc_inst_id  VARCHAR(64)  NOT NULL,                            -- 流程实例ID(逻辑外键 ACT_HI_PROCINST.ID_)
  biz_type      VARCHAR(32)  NOT NULL,                            -- 业务类型(如 ATT_LEAVE)
  biz_key       VARCHAR(64)  NOT NULL,                            -- 业务单号
  status        VARCHAR(16)  NOT NULL DEFAULT 'RUNNING',          -- 状态 RUNNING/FINISHED/TERMINATED
  created_by    BIGINT       NOT NULL DEFAULT 0,                  -- 创建人ID,0=系统
  updated_by    BIGINT       NOT NULL DEFAULT 0,                  -- 更新人ID
  created_at    TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,  -- 创建时间(UTC)
  updated_at    TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,  -- 更新时间(UTC,应用层维护)
  deleted       SMALLINT     NOT NULL DEFAULT 0,                  -- 逻辑删除 0=否 1=是
  version       INTEGER      NOT NULL DEFAULT 0,                  -- 乐观锁版本号
  CONSTRAINT pk_flow_ext_biz_relation PRIMARY KEY (id),
  CONSTRAINT uk_flow_biz_rel UNIQUE (tenant_id, biz_type, biz_key)
);
CREATE INDEX idx_flow_biz_rel_inst ON flow_ext_biz_relation (tenant_id, proc_inst_id);

-- 模型草稿/部署记录
CREATE TABLE flow_ext_model (
  id               BIGINT       NOT NULL,                         -- 主键: 雪花ID
  tenant_id        BIGINT       NOT NULL,                         -- 租户ID
  proc_def_key     VARCHAR(64)  NOT NULL,                         -- 流程定义Key
  version          INTEGER      NOT NULL DEFAULT 1,               -- 模型版本
  bpmn_xml         TEXT,                                          -- BPMN XML(设计态)
  form_schema_json JSONB,                                         -- 表单JSON Schema
  deploy_state     VARCHAR(16)  NOT NULL DEFAULT 'DRAFT',         -- 状态 DRAFT/DEPLOYED
  created_by       BIGINT       NOT NULL DEFAULT 0,               -- 创建人ID,0=系统
  updated_by       BIGINT       NOT NULL DEFAULT 0,               -- 更新人ID
  created_at       TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP, -- 创建时间(UTC)
  updated_at       TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP, -- 更新时间(UTC,应用层维护)
  deleted          SMALLINT     NOT NULL DEFAULT 0,               -- 逻辑删除 0=否 1=是
  CONSTRAINT pk_flow_ext_model PRIMARY KEY (id),
  CONSTRAINT uk_flow_model UNIQUE (tenant_id, proc_def_key, version)
);

-- 节点级表单绑定
CREATE TABLE flow_ext_node_form (
  id               BIGINT       NOT NULL,                         -- 主键: 雪花ID
  tenant_id        BIGINT       NOT NULL,                         -- 租户ID
  proc_def_id      VARCHAR(64)  NOT NULL,                         -- 流程定义ID
  node_id          VARCHAR(64)  NOT NULL,                         -- 节点ID
  form_schema_json JSONB,                                         -- 节点表单JSON Schema
  readonly_expr    VARCHAR(512),                                  -- 只读表达式
  created_by       BIGINT       NOT NULL DEFAULT 0,               -- 创建人ID,0=系统
  updated_by       BIGINT       NOT NULL DEFAULT 0,               -- 更新人ID
  created_at       TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP, -- 创建时间(UTC)
  updated_at       TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP, -- 更新时间(UTC,应用层维护)
  deleted          SMALLINT     NOT NULL DEFAULT 0,               -- 逻辑删除 0=否 1=是
  version          INTEGER      NOT NULL DEFAULT 0,               -- 乐观锁版本号
  CONSTRAINT pk_flow_ext_node_form PRIMARY KEY (id),
  CONSTRAINT uk_flow_node_form UNIQUE (tenant_id, proc_def_id, node_id)
);

-- 审批动作明细
CREATE TABLE flow_ext_task_action (
  id            BIGINT       NOT NULL,                            -- 主键: 雪花ID
  tenant_id     BIGINT       NOT NULL,                            -- 租户ID
  task_id       VARCHAR(64)  NOT NULL,                            -- 任务ID(逻辑外键 ACT_RU_TASK.ID_)
  action        VARCHAR(16)  NOT NULL,                            -- 动作 APPROVE/REJECT/TRANSFER/ADD_SIGN/WITHDRAW
  operator      BIGINT       NOT NULL,                            -- 操作人用户ID(逻辑外键 auth_user.id)
  comment       VARCHAR(512),                                     -- 审批意见
  form_values   JSONB,                                            -- 动态表单值(json，docs/19 §10.5-C11)
  delegate_from BIGINT,                                           -- 委托来源用户ID(加签/转办链)
  ts            TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,  -- 动作时间(UTC)
  created_by    BIGINT       NOT NULL DEFAULT 0,                  -- 创建人ID,0=系统
  updated_by    BIGINT       NOT NULL DEFAULT 0,                  -- 更新人ID
  created_at    TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,  -- 创建时间(UTC)
  updated_at    TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,  -- 更新时间(UTC,应用层维护)
  deleted       SMALLINT     NOT NULL DEFAULT 0,                  -- 逻辑删除 0=否 1=是
  version       INTEGER      NOT NULL DEFAULT 0,                  -- 乐观锁版本号
  CONSTRAINT pk_flow_ext_task_action PRIMARY KEY (id)
);
CREATE INDEX idx_flow_task_action ON flow_ext_task_action (tenant_id, task_id, created_at);

-- 事件出站表(事务消息兜底)
CREATE TABLE flow_ext_event_outbox (
  id            BIGINT       NOT NULL,                            -- 主键: 雪花ID
  event_id      VARCHAR(64)  NOT NULL,                            -- 事件ID(幂等)
  topic         VARCHAR(128) NOT NULL,                            -- MQ Topic
  payload       JSONB        NOT NULL,                            -- 事件载荷
  state         VARCHAR(16)  NOT NULL DEFAULT 'PENDING',          -- 状态 PENDING/SENT/DEAD
  retry_cnt     INTEGER      NOT NULL DEFAULT 0,                  -- 重试次数
  next_retry_at TIMESTAMP(3),                                     -- 下次重试时间(UTC)
  created_by    BIGINT       NOT NULL DEFAULT 0,                  -- 创建人ID,0=系统
  updated_by    BIGINT       NOT NULL DEFAULT 0,                  -- 更新人ID
  created_at    TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,  -- 创建时间(UTC)
  updated_at    TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,  -- 更新时间(UTC,应用层维护)
  deleted       SMALLINT     NOT NULL DEFAULT 0,                  -- 逻辑删除 0=否 1=是
  version       INTEGER      NOT NULL DEFAULT 0,                  -- 乐观锁版本号
  CONSTRAINT pk_flow_ext_event_outbox PRIMARY KEY (id),
  CONSTRAINT uk_flow_event_id UNIQUE (event_id)
);
CREATE INDEX idx_flow_outbox_state ON flow_ext_event_outbox (state, next_retry_at);

-- 节点超时催办/升级配置
CREATE TABLE flow_ext_timeout_cfg (
  id               BIGINT       NOT NULL,                         -- 主键: 雪花ID
  tenant_id        BIGINT       NOT NULL,                         -- 租户ID
  proc_def_id      VARCHAR(64)  NOT NULL,                         -- 流程定义ID
  node_id          VARCHAR(64)  NOT NULL,                         -- 节点ID
  remind_cron      VARCHAR(64),                                   -- 催办cron表达式
  escalate_after_h INTEGER,                                       -- 升级小时数
  escalate_to      BIGINT,                                        -- 升级目标用户ID
  created_by       BIGINT       NOT NULL DEFAULT 0,               -- 创建人ID,0=系统
  updated_by       BIGINT       NOT NULL DEFAULT 0,               -- 更新人ID
  created_at       TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP, -- 创建时间(UTC)
  updated_at       TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP, -- 更新时间(UTC,应用层维护)
  deleted          SMALLINT     NOT NULL DEFAULT 0,               -- 逻辑删除 0=否 1=是
  version          INTEGER      NOT NULL DEFAULT 0,               -- 乐观锁版本号
  CONSTRAINT pk_flow_ext_timeout_cfg PRIMARY KEY (id),
  CONSTRAINT uk_flow_timeout_cfg UNIQUE (tenant_id, proc_def_id, node_id)
);

-- 审批抄送记录(FLW-F-011)
CREATE TABLE flow_ext_cc (
  id               BIGINT       NOT NULL,                         -- 主键: 雪花ID
  tenant_id        BIGINT       NOT NULL,                         -- 租户ID
  proc_inst_id     VARCHAR(64)  NOT NULL,                         -- 流程实例ID(逻辑外键 ACT_HI_PROCINST.ID_)
  cc_node          VARCHAR(64)  NOT NULL,                         -- 抄送发起节点ID(BPMN userTask id)
  emp_id           BIGINT       NOT NULL,                         -- 被抄送人用户ID(逻辑外键 auth_user.id)
  created_by       BIGINT       NOT NULL DEFAULT 0,               -- 创建人ID,0=系统
  updated_by       BIGINT       NOT NULL DEFAULT 0,               -- 更新人ID
  created_at       TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP, -- 创建时间(UTC)
  updated_at       TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP, -- 更新时间(UTC,应用层维护)
  deleted          SMALLINT     NOT NULL DEFAULT 0,               -- 逻辑删除 0=否 1=是
  version          INTEGER      NOT NULL DEFAULT 0,               -- 乐观锁版本号
  CONSTRAINT pk_flow_ext_cc PRIMARY KEY (id),
  CONSTRAINT uk_flow_cc UNIQUE (tenant_id, proc_inst_id, cc_node, emp_id)
);
CREATE INDEX idx_flow_cc_emp ON flow_ext_cc (tenant_id, emp_id, created_at);

-- 审批委托代理(FLW-F-009)
CREATE TABLE flow_ext_delegate (
  id            BIGINT       NOT NULL,                            -- 主键: 雪花ID
  tenant_id     BIGINT       NOT NULL,                            -- 租户ID
  from_user     BIGINT       NOT NULL,                            -- 委托发起人用户ID(网关透传userId,逻辑外键 auth_user.id)
  to_user       BIGINT       NOT NULL,                            -- 被委托人用户ID(网关透传userId,逻辑外键 auth_user.id)
  proc_def_key  VARCHAR(64),                                      -- 流程定义Key(NULL=全部流程)
  start_at      TIMESTAMP(3) NOT NULL,                            -- 生效开始时间
  end_at        TIMESTAMP(3) NOT NULL,                            -- 生效结束时间
  status        VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE',           -- 状态 ACTIVE/ENDED
  reason        VARCHAR(255),                                     -- 委托原因
  created_by    BIGINT       NOT NULL DEFAULT 0,                  -- 创建人ID,0=系统
  updated_by    BIGINT       NOT NULL DEFAULT 0,                  -- 更新人ID
  created_at    TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,  -- 创建时间(UTC)
  updated_at    TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,  -- 更新时间(UTC,应用层维护)
  deleted       SMALLINT     NOT NULL DEFAULT 0,                  -- 逻辑删除 0=否 1=是
  version       INTEGER      NOT NULL DEFAULT 0,                  -- 乐观锁版本号
  CONSTRAINT pk_flow_ext_delegate PRIMARY KEY (id)
);
CREATE INDEX idx_flow_delegate_from ON flow_ext_delegate (tenant_id, from_user, status);
CREATE INDEX idx_flow_delegate_to ON flow_ext_delegate (tenant_id, to_user, status);

-- 任务节点附件(FLW-F-020)：仅办理人可传且任务未完成；文件本体在 PLT sys_file_meta
CREATE TABLE flow_ext_task_attachment (
  id            BIGINT       NOT NULL,                            -- 主键: 雪花ID
  tenant_id     BIGINT       NOT NULL,                            -- 租户ID
  instance_id   VARCHAR(64)  NOT NULL,                            -- 流程实例ID(逻辑外键 ACT_RU_TASK.PROC_INST_ID_)
  task_id       VARCHAR(64)  NOT NULL,                            -- 任务ID(逻辑外键 ACT_RU_TASK.ID_)
  file_id       VARCHAR(128) NOT NULL,                            -- PLT文件ID(sys_file_meta.id 或对象键)
  file_name     VARCHAR(255) NOT NULL,                            -- 文件名快照
  uploaded_by   BIGINT       NOT NULL,                            -- 上传人用户ID(逻辑外键 auth_user.id)
  created_by    BIGINT       NOT NULL DEFAULT 0,                  -- 创建人ID,0=系统
  updated_by    BIGINT       NOT NULL DEFAULT 0,                  -- 更新人ID
  created_at    TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,  -- 创建时间(UTC)
  updated_at    TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,  -- 更新时间(UTC,应用层维护)
  deleted       SMALLINT     NOT NULL DEFAULT 0,                  -- 逻辑删除 0=否 1=是
  version       INTEGER      NOT NULL DEFAULT 0,                  -- 乐观锁版本号
  CONSTRAINT pk_flow_ext_task_attachment PRIMARY KEY (id),
  CONSTRAINT uk_flow_task_attachment UNIQUE (tenant_id, task_id, file_id)
);
CREATE INDEX idx_flow_task_attachment_inst ON flow_ext_task_attachment (tenant_id, instance_id, created_at);

-- 任务结构化审批意见标签(FLW-F-020)：与 flow_ext_task_action.comment 自由文本互补
CREATE TABLE flow_ext_task_opinion_tag (
  id            BIGINT       NOT NULL,                            -- 主键: 雪花ID
  tenant_id     BIGINT       NOT NULL,                            -- 租户ID
  task_id       VARCHAR(64)  NOT NULL,                            -- 任务ID(逻辑外键 ACT_RU_TASK.ID_)
  tag           VARCHAR(32)  NOT NULL,                            -- 标签 RECHECK_DATA/NEED_MORE_INFO/COMPLIANT/OTHER
  comment       VARCHAR(512),                                     -- 标签说明
  operator      BIGINT       NOT NULL,                            -- 打标人用户ID(逻辑外键 auth_user.id)
  created_by    BIGINT       NOT NULL DEFAULT 0,                  -- 创建人ID,0=系统
  updated_by    BIGINT       NOT NULL DEFAULT 0,                  -- 更新人ID
  created_at    TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,  -- 创建时间(UTC)
  updated_at    TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,  -- 更新时间(UTC,应用层维护)
  deleted       SMALLINT     NOT NULL DEFAULT 0,                  -- 逻辑删除 0=否 1=是
  version       INTEGER      NOT NULL DEFAULT 0,                  -- 乐观锁版本号
  CONSTRAINT pk_flow_ext_task_opinion_tag PRIMARY KEY (id)
);
CREATE INDEX idx_flow_task_opinion_tag ON flow_ext_task_opinion_tag (tenant_id, task_id, created_at);
CREATE INDEX idx_flow_task_opinion_tag_val ON flow_ext_task_opinion_tag (tenant_id, tag, created_at);

-- =====================================================================
-- 域 9: rag_db 知识库与问答
-- =====================================================================
CREATE SCHEMA IF NOT EXISTS rag_db;
SET search_path TO rag_db;

-- 知识空间
CREATE TABLE kb_space (
  id            BIGINT       NOT NULL,                            -- 主键: 雪花ID
  tenant_id     BIGINT       NOT NULL,                            -- 租户ID
  name          VARCHAR(128) NOT NULL,                            -- 空间名称
  code          VARCHAR(64)  NOT NULL,                            -- 空间编码
  description   VARCHAR(512),                                     -- 空间描述
  created_by    BIGINT       NOT NULL DEFAULT 0,                  -- 创建人ID,0=系统
  updated_by    BIGINT       NOT NULL DEFAULT 0,                  -- 更新人ID
  created_at    TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,  -- 创建时间(UTC)
  updated_at    TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,  -- 更新时间(UTC,应用层维护)
  deleted       SMALLINT     NOT NULL DEFAULT 0,                  -- 逻辑删除 0=否 1=是
  version       INTEGER      NOT NULL DEFAULT 0,                  -- 乐观锁版本号
  CONSTRAINT pk_kb_space PRIMARY KEY (id),
  CONSTRAINT uk_kb_space_code UNIQUE (tenant_id, code)
);

-- 空间成员
CREATE TABLE kb_space_member (
  id            BIGINT       NOT NULL,                            -- 主键: 雪花ID
  tenant_id     BIGINT       NOT NULL,                            -- 租户ID
  space_id      BIGINT       NOT NULL,                            -- 空间ID(逻辑外键 kb_space.id)
  user_id       BIGINT       NOT NULL,                            -- 用户ID(逻辑外键 auth_user.id)
  role          VARCHAR(16)  NOT NULL DEFAULT 'MEMBER',           -- 成员角色 OWNER/EDITOR/VIEWER
  created_by    BIGINT       NOT NULL DEFAULT 0,                  -- 创建人ID,0=系统
  updated_by    BIGINT       NOT NULL DEFAULT 0,                  -- 更新人ID
  created_at    TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,  -- 创建时间(UTC)
  updated_at    TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,  -- 更新时间(UTC,应用层维护)
  deleted       SMALLINT     NOT NULL DEFAULT 0,                  -- 逻辑删除 0=否 1=是
  version       INTEGER      NOT NULL DEFAULT 0,                  -- 乐观锁版本号
  CONSTRAINT pk_kb_space_member PRIMARY KEY (id),
  CONSTRAINT uk_kb_space_member UNIQUE (tenant_id, space_id, user_id)
);

-- 权限标签字典
CREATE TABLE kb_acl_tag (
  id            BIGINT       NOT NULL,                            -- 主键: 雪花ID
  tenant_id     BIGINT       NOT NULL,                            -- 租户ID
  name          VARCHAR(64)  NOT NULL,                            -- 标签名称
  code          VARCHAR(64)  NOT NULL,                            -- 标签编码(密级/部门/角色)
  tag_type      VARCHAR(16)  NOT NULL,                            -- 标签类型 SECRET_LEVEL/DEPT/ROLE
  created_by    BIGINT       NOT NULL DEFAULT 0,                  -- 创建人ID,0=系统
  updated_by    BIGINT       NOT NULL DEFAULT 0,                  -- 更新人ID
  created_at    TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,  -- 创建时间(UTC)
  updated_at    TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,  -- 更新时间(UTC,应用层维护)
  deleted       SMALLINT     NOT NULL DEFAULT 0,                  -- 逻辑删除 0=否 1=是
  version       INTEGER      NOT NULL DEFAULT 0,                  -- 乐观锁版本号
  CONSTRAINT pk_kb_acl_tag PRIMARY KEY (id),
  CONSTRAINT uk_kb_acl_tag_code UNIQUE (tenant_id, code)
);

-- 敏感词库(KBD-F-017)：上传/建文档/建切片时扫描，BLOCK 拒绝落库，WARN 放行并回传 warnings
CREATE TABLE kb_sensitive_word (
  id            BIGINT       NOT NULL,                            -- 主键: 雪花ID
  tenant_id     BIGINT       NOT NULL,                            -- 租户ID
  word          VARCHAR(64)  NOT NULL,                            -- 敏感词
  level         VARCHAR(16)  NOT NULL DEFAULT 'WARN',             -- 级别 BLOCK=拦截/WARN=告警放行
  created_by    BIGINT       NOT NULL DEFAULT 0,                  -- 创建人ID,0=系统
  updated_by    BIGINT       NOT NULL DEFAULT 0,                  -- 更新人ID
  created_at    TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,  -- 创建时间(UTC)
  updated_at    TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,  -- 更新时间(UTC,应用层维护)
  deleted       SMALLINT     NOT NULL DEFAULT 0,                  -- 逻辑删除 0=否 1=是
  version       INTEGER      NOT NULL DEFAULT 0,                  -- 乐观锁版本号
  CONSTRAINT pk_kb_sensitive_word PRIMARY KEY (id),
  CONSTRAINT uk_kb_sensitive_word UNIQUE (tenant_id, word)
);

-- 种子（演示租户 100001；其余租户由管理端自行维护）
INSERT INTO kb_sensitive_word (id, tenant_id, word, level) VALUES
  (9000000000000000001, 100001, '赌博', 'BLOCK'),
  (9000000000000000002, 100001, '毒品', 'BLOCK'),
  (9000000000000000003, 100001, '枪支', 'BLOCK'),
  (9000000000000000004, 100001, '传销', 'BLOCK'),
  (9000000000000000005, 100001, '薪酬明细', 'BLOCK'),
  (9000000000000000006, 100001, '工资明细', 'BLOCK'),
  (9000000000000000007, 100001, '内部未公开', 'WARN'),
  (9000000000000000008, 100001, '机密材料', 'WARN')
ON CONFLICT (tenant_id, word) DO NOTHING;

-- 文档主档
CREATE TABLE kb_document (
  id            BIGINT       NOT NULL,                            -- 主键: 雪花ID
  tenant_id     BIGINT       NOT NULL,                            -- 租户ID
  space_id      BIGINT       NOT NULL,                            -- 空间ID(逻辑外键 kb_space.id)
  title         VARCHAR(255) NOT NULL,                            -- 文档标题
  title_hash    VARCHAR(64)  NOT NULL,                            -- 标题哈希(去重)
  doc_type      VARCHAR(16)  NOT NULL DEFAULT 'DOC',              -- 类型 DOC/FAQ/TABLE
  file_id       BIGINT,                                           -- 源文件ID(逻辑外键 sys_file_meta.id)
  created_by    BIGINT       NOT NULL DEFAULT 0,                  -- 创建人ID,0=系统
  updated_by    BIGINT       NOT NULL DEFAULT 0,                  -- 更新人ID
  created_at    TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,  -- 创建时间(UTC)
  updated_at    TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,  -- 更新时间(UTC,应用层维护)
  deleted       SMALLINT     NOT NULL DEFAULT 0,                  -- 逻辑删除 0=否 1=是
  version       INTEGER      NOT NULL DEFAULT 0,                  -- 乐观锁版本号
  CONSTRAINT pk_kb_document PRIMARY KEY (id),
  CONSTRAINT uk_kb_document UNIQUE (tenant_id, space_id, title_hash)
);

-- 文档版本(状态机)
CREATE TABLE kb_doc_version (
  id                BIGINT       NOT NULL,                        -- 主键: 雪花ID
  tenant_id         BIGINT       NOT NULL,                        -- 租户ID
  document_id       BIGINT       NOT NULL,                        -- 文档ID(逻辑外键 kb_document.id)
  version           INTEGER      NOT NULL DEFAULT 1,              -- 版本号
  status            VARCHAR(16)  NOT NULL DEFAULT 'DRAFT',        -- 状态 DRAFT/PENDING/PUBLISHED/EXPIRED/ARCHIVED
  effective_from    DATE,                                         -- 生效开始日
  effective_to      DATE,                                         -- 生效结束日
  milvus_collection VARCHAR(64),                                  -- Milvus Collection名
  created_by        BIGINT       NOT NULL DEFAULT 0,              -- 创建人ID,0=系统
  updated_by        BIGINT       NOT NULL DEFAULT 0,              -- 更新人ID
  created_at        TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP, -- 创建时间(UTC)
  updated_at        TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP, -- 更新时间(UTC,应用层维护)
  deleted           SMALLINT     NOT NULL DEFAULT 0,              -- 逻辑删除 0=否 1=是
  CONSTRAINT pk_kb_doc_version PRIMARY KEY (id)
  -- 原为 uk_kb_doc_version UNIQUE (tenant_id, document_id, version)：
  -- version 列被实体层用作乐观锁（BaseEntity 约定），每次 update 自增，
  -- 唯一键在该列上必然碰撞（docs/19 §9 联调发现），降级为普通索引。
);
CREATE INDEX idx_kb_doc_version_doc ON kb_doc_version (tenant_id, document_id);
);

-- 切片
CREATE TABLE kb_chunk (
  id             BIGINT       NOT NULL,                           -- 主键: 雪花ID
  tenant_id      BIGINT       NOT NULL,                           -- 租户ID
  doc_version_id BIGINT       NOT NULL,                           -- 文档版本ID(逻辑外键 kb_doc_version.id)
  space_id       BIGINT       NOT NULL,                           -- 空间ID(逻辑外键 kb_space.id)
  seq            INTEGER      NOT NULL,                           -- 切片序号
  heading_path   VARCHAR(512),                                    -- 标题层级路径
  text           TEXT,                                            -- 切片文本(占位; chunk 本体入 Milvus/ES)
  token_cnt      INTEGER      NOT NULL DEFAULT 0,                 -- token数
  milvus_id      VARCHAR(64),                                     -- Milvus向量ID
  es_id          VARCHAR(64),                                     -- ES文档ID
  acl_tags       JSONB,                                           -- 权限标签(json)
  created_by     BIGINT       NOT NULL DEFAULT 0,                 -- 创建人ID,0=系统
  updated_by     BIGINT       NOT NULL DEFAULT 0,                 -- 更新人ID
  created_at     TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP, -- 创建时间(UTC)
  updated_at     TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP, -- 更新时间(UTC,应用层维护)
  deleted        SMALLINT     NOT NULL DEFAULT 0,                 -- 逻辑删除 0=否 1=是
  version        INTEGER      NOT NULL DEFAULT 0,                 -- 乐观锁版本号
  CONSTRAINT pk_kb_chunk PRIMARY KEY (id)
);
CREATE INDEX idx_kb_chunk_version ON kb_chunk (tenant_id, doc_version_id, seq);
CREATE INDEX idx_kb_chunk_space ON kb_chunk (tenant_id, space_id);

-- 问答会话
CREATE TABLE qa_session (
  id            BIGINT       NOT NULL,                            -- 主键: 雪花ID
  tenant_id     BIGINT       NOT NULL,                            -- 租户ID
  user_id       BIGINT       NOT NULL,                            -- 用户ID(逻辑外键 auth_user.id)
  title         VARCHAR(128),                                     -- 会话标题
  created_by    BIGINT       NOT NULL DEFAULT 0,                  -- 创建人ID,0=系统
  updated_by    BIGINT       NOT NULL DEFAULT 0,                  -- 更新人ID
  created_at    TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,  -- 创建时间(UTC)
  updated_at    TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,  -- 更新时间(UTC,应用层维护)
  deleted       SMALLINT     NOT NULL DEFAULT 0,                  -- 逻辑删除 0=否 1=是
  version       INTEGER      NOT NULL DEFAULT 0,                  -- 乐观锁版本号
  CONSTRAINT pk_qa_session PRIMARY KEY (id)
);
CREATE INDEX idx_qa_session_user ON qa_session (tenant_id, user_id, created_at);

-- 问答消息
CREATE TABLE qa_message (
  id            BIGINT       NOT NULL,                            -- 主键: 雪花ID
  tenant_id     BIGINT       NOT NULL,                            -- 租户ID
  session_id    BIGINT       NOT NULL,                            -- 会话ID(逻辑外键 qa_session.id)
  role          VARCHAR(16)  NOT NULL,                            -- 角色 USER/ASSISTANT
  question      TEXT,                                             -- 问题(用户消息)
  answer        TEXT,                                             -- 回答(助手消息)
  refs          JSONB,                                            -- 引用列表(json)
  tool_calls    JSONB,                                            -- 工具调用(json)
  latency_ms    INTEGER,                                          -- 响应延迟(ms)
  model         VARCHAR(64),                                      -- 模型标识
  answer_status VARCHAR(32),                                      -- 回答状态 ANSWERED/NO_RECALL
  created_by    BIGINT       NOT NULL DEFAULT 0,                  -- 创建人ID,0=系统
  updated_by    BIGINT       NOT NULL DEFAULT 0,                  -- 更新人ID
  created_at    TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,  -- 创建时间(UTC)
  updated_at    TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,  -- 更新时间(UTC,应用层维护)
  deleted       SMALLINT     NOT NULL DEFAULT 0,                  -- 逻辑删除 0=否 1=是
  version       INTEGER      NOT NULL DEFAULT 0,                  -- 乐观锁版本号
  CONSTRAINT pk_qa_message PRIMARY KEY (id)
);
CREATE INDEX idx_qa_message_session ON qa_message (tenant_id, session_id, created_at);

-- 问答反馈
CREATE TABLE qa_feedback (
  id              BIGINT       NOT NULL,                          -- 主键: 雪花ID
  tenant_id       BIGINT       NOT NULL,                          -- 租户ID
  message_id      BIGINT       NOT NULL,                          -- 消息ID(逻辑外键 qa_message.id)
  type            VARCHAR(16)  NOT NULL,                          -- 反馈类型 LIKE/DISLIKE
  correction_text VARCHAR(512),                                   -- 纠错文本
  created_by      BIGINT       NOT NULL DEFAULT 0,                -- 创建人ID,0=系统
  updated_by      BIGINT       NOT NULL DEFAULT 0,                -- 更新人ID
  created_at      TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP, -- 创建时间(UTC)
  updated_at      TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP, -- 更新时间(UTC,应用层维护)
  deleted         SMALLINT     NOT NULL DEFAULT 0,                -- 逻辑删除 0=否 1=是
  version         INTEGER      NOT NULL DEFAULT 0,                -- 乐观锁版本号
  CONSTRAINT pk_qa_feedback PRIMARY KEY (id)
);
CREATE INDEX idx_qa_feedback_msg ON qa_feedback (tenant_id, message_id);

-- 知识治理工单
CREATE TABLE kb_govern_ticket (
  id             BIGINT       NOT NULL,                           -- 主键: 雪花ID
  tenant_id      BIGINT       NOT NULL,                           -- 租户ID
  doc_version_id BIGINT,                                          -- 文档版本ID(逻辑外键 kb_doc_version.id)
  source         VARCHAR(16)  NOT NULL,                           -- 来源 FEEDBACK/AUDIT
  status         VARCHAR(16)  NOT NULL DEFAULT 'OPEN',            -- 状态 OPEN/PROCESSING/CLOSED
  description    VARCHAR(512),                                    -- 工单描述
  created_by     BIGINT       NOT NULL DEFAULT 0,                 -- 创建人ID,0=系统
  updated_by     BIGINT       NOT NULL DEFAULT 0,                 -- 更新人ID
  created_at     TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP, -- 创建时间(UTC)
  updated_at     TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP, -- 更新时间(UTC,应用层维护)
  deleted        SMALLINT     NOT NULL DEFAULT 0,                 -- 逻辑删除 0=否 1=是
  version        INTEGER      NOT NULL DEFAULT 0,                 -- 乐观锁版本号
  CONSTRAINT pk_kb_govern_ticket PRIMARY KEY (id)
);
CREATE INDEX idx_kb_govern_ticket ON kb_govern_ticket (tenant_id, doc_version_id, status);

-- 评测集
CREATE TABLE kb_eval_set (
  id            BIGINT       NOT NULL,                            -- 主键: 雪花ID
  tenant_id     BIGINT       NOT NULL,                            -- 租户ID
  name          VARCHAR(128) NOT NULL,                            -- 评测集名称
  code          VARCHAR(64)  NOT NULL,                            -- 评测集编码
  created_by    BIGINT       NOT NULL DEFAULT 0,                  -- 创建人ID,0=系统
  updated_by    BIGINT       NOT NULL DEFAULT 0,                  -- 更新人ID
  created_at    TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,  -- 创建时间(UTC)
  updated_at    TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,  -- 更新时间(UTC,应用层维护)
  deleted       SMALLINT     NOT NULL DEFAULT 0,                  -- 逻辑删除 0=否 1=是
  version       INTEGER      NOT NULL DEFAULT 0,                  -- 乐观锁版本号
  CONSTRAINT pk_kb_eval_set PRIMARY KEY (id),
  CONSTRAINT uk_kb_eval_set_code UNIQUE (tenant_id, code)
);

-- 评测用例
CREATE TABLE kb_eval_case (
  id            BIGINT       NOT NULL,                            -- 主键: 雪花ID
  tenant_id     BIGINT       NOT NULL,                            -- 租户ID
  eval_set_id   BIGINT       NOT NULL,                            -- 评测集ID(逻辑外键 kb_eval_set.id)
  question      VARCHAR(512) NOT NULL,                            -- 问题
  expected_refs JSONB,                                            -- 期望引用(json)
  ground_truth  VARCHAR(512),                                     -- 标准答案
  created_by    BIGINT       NOT NULL DEFAULT 0,                  -- 创建人ID,0=系统
  updated_by    BIGINT       NOT NULL DEFAULT 0,                  -- 更新人ID
  created_at    TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,  -- 创建时间(UTC)
  updated_at    TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,  -- 更新时间(UTC,应用层维护)
  deleted       SMALLINT     NOT NULL DEFAULT 0,                  -- 逻辑删除 0=否 1=是
  version       INTEGER      NOT NULL DEFAULT 0,                  -- 乐观锁版本号
  CONSTRAINT pk_kb_eval_case PRIMARY KEY (id)
);
CREATE INDEX idx_kb_eval_case_set ON kb_eval_case (tenant_id, eval_set_id);

-- 评测运行
CREATE TABLE kb_eval_run (
  id            BIGINT       NOT NULL,                            -- 主键: 雪花ID
  tenant_id     BIGINT       NOT NULL,                            -- 租户ID
  eval_set_id   BIGINT       NOT NULL,                            -- 评测集ID(逻辑外键 kb_eval_set.id)
  metrics       JSONB,                                            -- 指标快照(json)
  status        VARCHAR(16)  NOT NULL DEFAULT 'RUNNING',          -- 状态 RUNNING/DONE/FAILED
  created_by    BIGINT       NOT NULL DEFAULT 0,                  -- 创建人ID,0=系统
  updated_by    BIGINT       NOT NULL DEFAULT 0,                  -- 更新人ID
  created_at    TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,  -- 创建时间(UTC)
  updated_at    TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,  -- 更新时间(UTC,应用层维护)
  deleted       SMALLINT     NOT NULL DEFAULT 0,                  -- 逻辑删除 0=否 1=是
  version       INTEGER      NOT NULL DEFAULT 0,                  -- 乐观锁版本号
  CONSTRAINT pk_kb_eval_run PRIMARY KEY (id)
);
CREATE INDEX idx_kb_eval_run_set ON kb_eval_run (tenant_id, eval_set_id, created_at);

-- =====================================================================
-- 域 10: plt_db 平台支撑
-- =====================================================================
CREATE SCHEMA IF NOT EXISTS plt_db;
SET search_path TO plt_db;

-- 字典类型(广播表)
CREATE TABLE sys_dict_type (
  id            BIGINT       NOT NULL,                            -- 主键: 雪花ID
  tenant_id     BIGINT       NOT NULL,                            -- 租户ID
  name          VARCHAR(64)  NOT NULL,                            -- 字典类型名称
  code          VARCHAR(64)  NOT NULL,                            -- 字典类型编码
  created_by    BIGINT       NOT NULL DEFAULT 0,                  -- 创建人ID,0=系统
  updated_by    BIGINT       NOT NULL DEFAULT 0,                  -- 更新人ID
  created_at    TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,  -- 创建时间(UTC)
  updated_at    TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,  -- 更新时间(UTC,应用层维护)
  deleted       SMALLINT     NOT NULL DEFAULT 0,                  -- 逻辑删除 0=否 1=是
  version       INTEGER      NOT NULL DEFAULT 0,                  -- 乐观锁版本号
  CONSTRAINT pk_sys_dict_type PRIMARY KEY (id),
  CONSTRAINT uk_sys_dict_type_code UNIQUE (tenant_id, code)
);

-- 字典项(广播表)
CREATE TABLE sys_dict_item (
  id            BIGINT       NOT NULL,                            -- 主键: 雪花ID
  tenant_id     BIGINT       NOT NULL,                            -- 租户ID
  type_code     VARCHAR(64)  NOT NULL,                            -- 字典类型编码(逻辑外键 sys_dict_type.code)
  label         VARCHAR(64)  NOT NULL,                            -- 字典项标签
  value         VARCHAR(64)  NOT NULL,                            -- 字典项值
  sort          INTEGER      NOT NULL DEFAULT 0,                  -- 排序
  created_by    BIGINT       NOT NULL DEFAULT 0,                  -- 创建人ID,0=系统
  updated_by    BIGINT       NOT NULL DEFAULT 0,                  -- 更新人ID
  created_at    TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,  -- 创建时间(UTC)
  updated_at    TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,  -- 更新时间(UTC,应用层维护)
  deleted       SMALLINT     NOT NULL DEFAULT 0,                  -- 逻辑删除 0=否 1=是
  version       INTEGER      NOT NULL DEFAULT 0,                  -- 乐观锁版本号
  CONSTRAINT pk_sys_dict_item PRIMARY KEY (id),
  CONSTRAINT uk_sys_dict_item UNIQUE (tenant_id, type_code, value)
);

-- 租户参数(广播表)
CREATE TABLE sys_tenant_config (
  id            BIGINT       NOT NULL,                            -- 主键: 雪花ID
  tenant_id     BIGINT       NOT NULL,                            -- 租户ID
  config_key    VARCHAR(64)  NOT NULL,                            -- 配置键
  config_value  VARCHAR(512),                                     -- 配置值
  created_by    BIGINT       NOT NULL DEFAULT 0,                  -- 创建人ID,0=系统
  updated_by    BIGINT       NOT NULL DEFAULT 0,                  -- 更新人ID
  created_at    TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,  -- 创建时间(UTC)
  updated_at    TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,  -- 更新时间(UTC,应用层维护)
  deleted       SMALLINT     NOT NULL DEFAULT 0,                  -- 逻辑删除 0=否 1=是
  version       INTEGER      NOT NULL DEFAULT 0,                  -- 乐观锁版本号
  CONSTRAINT pk_sys_tenant_config PRIMARY KEY (id),
  CONSTRAINT uk_sys_tenant_config UNIQUE (tenant_id, config_key)
);

-- 文件元数据
CREATE TABLE sys_file_meta (
  id               BIGINT       NOT NULL,                         -- 主键: 雪花ID
  tenant_id        BIGINT       NOT NULL,                         -- 租户ID
  bucket           VARCHAR(64)  NOT NULL,                         -- MinIO桶
  object_key       VARCHAR(255) NOT NULL,                         -- 对象键
  file_name        VARCHAR(255) NOT NULL,                         -- 原始文件名
  content_type     VARCHAR(128),                                  -- 内容类型
  size_bytes       BIGINT       NOT NULL DEFAULT 0,               -- 文件大小(字节)
  watermark_policy VARCHAR(16),                                   -- 水印策略
  created_by       BIGINT       NOT NULL DEFAULT 0,               -- 创建人ID,0=系统
  updated_by       BIGINT       NOT NULL DEFAULT 0,               -- 更新人ID
  created_at       TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP, -- 创建时间(UTC)
  updated_at       TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP, -- 更新时间(UTC,应用层维护)
  deleted          SMALLINT     NOT NULL DEFAULT 0,               -- 逻辑删除 0=否 1=是
  version          INTEGER      NOT NULL DEFAULT 0,               -- 乐观锁版本号
  CONSTRAINT pk_sys_file_meta PRIMARY KEY (id),
  CONSTRAINT uk_sys_file_meta UNIQUE (tenant_id, bucket, object_key)
);

-- 通知模板
CREATE TABLE sys_notify_template (
  id            BIGINT       NOT NULL,                            -- 主键: 雪花ID
  tenant_id     BIGINT       NOT NULL,                            -- 租户ID
  code          VARCHAR(64)  NOT NULL,                            -- 模板编码
  channel       VARCHAR(16)  NOT NULL,                            -- 渠道 EMAIL/SMS/APP/WECOM
  subject       VARCHAR(128),                                     -- 标题
  content       TEXT,                                             -- 模板内容
  webhook_url   VARCHAR(512),                                     -- webhook机器人地址(DINGTALK/WECOM/WEBHOOK渠道用)
  created_by    BIGINT       NOT NULL DEFAULT 0,                  -- 创建人ID,0=系统
  updated_by    BIGINT       NOT NULL DEFAULT 0,                  -- 更新人ID
  created_at    TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,  -- 创建时间(UTC)
  updated_at    TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,  -- 更新时间(UTC,应用层维护)
  deleted       SMALLINT     NOT NULL DEFAULT 0,                  -- 逻辑删除 0=否 1=是
  version       INTEGER      NOT NULL DEFAULT 0,                  -- 乐观锁版本号
  CONSTRAINT pk_sys_notify_template PRIMARY KEY (id),
  CONSTRAINT uk_sys_notify_template UNIQUE (tenant_id, code, channel)
);

-- 通知记录
CREATE TABLE sys_notify_record (
  id            BIGINT       NOT NULL,                            -- 主键: 雪花ID
  tenant_id     BIGINT       NOT NULL,                            -- 租户ID
  user_id       BIGINT       NOT NULL,                            -- 接收人用户ID(逻辑外键 auth_user.id)
  template_id   BIGINT,                                           -- 模板ID(逻辑外键 sys_notify_template.id)
  channel       VARCHAR(16)  NOT NULL,                            -- 渠道
  send_status   VARCHAR(16)  NOT NULL DEFAULT 'PENDING',          -- 状态 PENDING/SENT/FAILED
  sent_at       TIMESTAMP(3),                                     -- 发送时间(UTC)
  created_by    BIGINT       NOT NULL DEFAULT 0,                  -- 创建人ID,0=系统
  updated_by    BIGINT       NOT NULL DEFAULT 0,                  -- 更新人ID
  created_at    TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,  -- 创建时间(UTC)
  updated_at    TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,  -- 更新时间(UTC,应用层维护)
  deleted       SMALLINT     NOT NULL DEFAULT 0,                  -- 逻辑删除 0=否 1=是
  version       INTEGER      NOT NULL DEFAULT 0,                  -- 乐观锁版本号
  CONSTRAINT pk_sys_notify_record PRIMARY KEY (id)
);
CREATE INDEX idx_sys_notify_record ON sys_notify_record (tenant_id, user_id, created_at);

-- 报表定义
CREATE TABLE sys_report_def (
  id            BIGINT       NOT NULL,                            -- 主键: 雪花ID
  tenant_id     BIGINT       NOT NULL,                            -- 租户ID
  name          VARCHAR(128) NOT NULL,                            -- 报表名称
  code          VARCHAR(64)  NOT NULL,                            -- 报表编码
  def_json      JSONB,                                            -- 报表定义(json)
  created_by    BIGINT       NOT NULL DEFAULT 0,                  -- 创建人ID,0=系统
  updated_by    BIGINT       NOT NULL DEFAULT 0,                  -- 更新人ID
  created_at    TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,  -- 创建时间(UTC)
  updated_at    TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,  -- 更新时间(UTC,应用层维护)
  deleted       SMALLINT     NOT NULL DEFAULT 0,                  -- 逻辑删除 0=否 1=是
  version       INTEGER      NOT NULL DEFAULT 0,                  -- 乐观锁版本号
  CONSTRAINT pk_sys_report_def PRIMARY KEY (id),
  CONSTRAINT uk_sys_report_def_code UNIQUE (tenant_id, code)
);

-- 异步导出任务
CREATE TABLE sys_export_task (
  id            BIGINT       NOT NULL,                            -- 主键: 雪花ID
  tenant_id     BIGINT       NOT NULL,                            -- 租户ID
  report_def_id BIGINT,                                           -- 报表定义ID(逻辑外键 sys_report_def.id)
  status        VARCHAR(16)  NOT NULL DEFAULT 'PENDING',          -- 状态 PENDING/RUNNING/DONE/FAILED
  file_id       BIGINT,                                           -- 导出文件ID(逻辑外键 sys_file_meta.id)
  created_by    BIGINT       NOT NULL DEFAULT 0,                  -- 创建人ID,0=系统
  updated_by    BIGINT       NOT NULL DEFAULT 0,                  -- 更新人ID
  created_at    TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,  -- 创建时间(UTC)
  updated_at    TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,  -- 更新时间(UTC,应用层维护)
  deleted       SMALLINT     NOT NULL DEFAULT 0,                  -- 逻辑删除 0=否 1=是
  version       INTEGER      NOT NULL DEFAULT 0,                  -- 乐观锁版本号
  CONSTRAINT pk_sys_export_task PRIMARY KEY (id)
);
CREATE INDEX idx_sys_export_task ON sys_export_task (tenant_id, status, created_at);

-- 审计日志(只追加, 月分区; 分区由调度任务维护)
CREATE TABLE sys_audit_log (
  id            BIGINT       NOT NULL,                            -- 主键: 雪花ID
  tenant_id     BIGINT       NOT NULL,                            -- 租户ID
  actor_id      BIGINT,                                           -- 操作人用户ID
  action        VARCHAR(64)  NOT NULL,                            -- 审计动作
  resource      VARCHAR(128),                                     -- 资源标识
  detail        JSONB,                                            -- 审计明细(json)
  ip            VARCHAR(64),                                      -- 来源IP
  created_at    TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,  -- 创建时间(UTC,分区键)
  CONSTRAINT pk_sys_audit_log PRIMARY KEY (id, created_at)
) PARTITION BY RANGE (created_at);

CREATE TABLE sys_audit_log_202601 PARTITION OF sys_audit_log FOR VALUES FROM ('2026-01-01') TO ('2026-02-01');
CREATE TABLE sys_audit_log_202602 PARTITION OF sys_audit_log FOR VALUES FROM ('2026-02-01') TO ('2026-03-01');
CREATE TABLE sys_audit_log_202603 PARTITION OF sys_audit_log FOR VALUES FROM ('2026-03-01') TO ('2026-04-01');
CREATE INDEX idx_sys_audit_actor ON sys_audit_log (tenant_id, actor_id);
CREATE INDEX idx_sys_audit_time ON sys_audit_log (tenant_id, created_at);

-- 调度任务注册
CREATE TABLE sys_scheduler_task_registry (
  id            BIGINT       NOT NULL,                            -- 主键: 雪花ID
  tenant_id     BIGINT       NOT NULL,                            -- 租户ID
  task_key      VARCHAR(64)  NOT NULL,                            -- 任务键
  cron          VARCHAR(64)  NOT NULL,                            -- cron表达式
  handler       VARCHAR(128) NOT NULL,                            -- 处理器标识
  status        SMALLINT     NOT NULL DEFAULT 1,                  -- 状态 1=启用 0=停用
  created_by    BIGINT       NOT NULL DEFAULT 0,                  -- 创建人ID,0=系统
  updated_by    BIGINT       NOT NULL DEFAULT 0,                  -- 更新人ID
  created_at    TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,  -- 创建时间(UTC)
  updated_at    TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,  -- 更新时间(UTC,应用层维护)
  deleted       SMALLINT     NOT NULL DEFAULT 0,                  -- 逻辑删除 0=否 1=是
  version       INTEGER      NOT NULL DEFAULT 0,                  -- 乐观锁版本号
  CONSTRAINT pk_sys_scheduler_task_registry PRIMARY KEY (id),
  CONSTRAINT uk_sys_scheduler_task UNIQUE (tenant_id, task_key)
);

-- 全局事件表(事务消息模板)
CREATE TABLE sys_outbox_message (
  id            BIGINT       NOT NULL,                            -- 主键: 雪花ID
  event_id      VARCHAR(64)  NOT NULL,                            -- 事件ID(幂等)
  topic         VARCHAR(128) NOT NULL,                            -- MQ Topic
  payload       JSONB        NOT NULL,                            -- 事件载荷
  state         VARCHAR(16)  NOT NULL DEFAULT 'PENDING',          -- 状态 PENDING/SENT/DEAD
  retry_cnt     INTEGER      NOT NULL DEFAULT 0,                  -- 重试次数
  next_retry_at TIMESTAMP(3),                                     -- 下次重试时间(UTC)
  created_at    TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,  -- 创建时间(UTC)
  CONSTRAINT pk_sys_outbox_message PRIMARY KEY (id),
  CONSTRAINT uk_sys_outbox_event UNIQUE (event_id)
);
CREATE INDEX idx_sys_outbox_state ON sys_outbox_message (state, next_retry_at);

-- 通知渠道偏好(PLT-F-004)
CREATE TABLE sys_notify_preference (
  id           BIGINT      NOT NULL,                              -- 主键: 雪花ID
  tenant_id    BIGINT      NOT NULL,                              -- 租户ID
  user_id      BIGINT      NOT NULL,                              -- 用户ID(网关透传userId,逻辑外键 auth_user.id)
  channel      VARCHAR(16) NOT NULL,                              -- 渠道 DINGTALK/WECOM/WEBHOOK
  enabled      SMALLINT    NOT NULL DEFAULT 1,                    -- 是否启用 0=禁用 1=启用
  quiet_start  TIME,                                              -- 免打扰开始时间(NULL=不启用,支持跨午夜)
  quiet_end    TIME,                                              -- 免打扰结束时间(NULL=不启用,支持跨午夜)
  created_by   BIGINT      NOT NULL DEFAULT 0,                    -- 创建人ID,0=系统
  updated_by   BIGINT      NOT NULL DEFAULT 0,                    -- 更新人ID
  created_at   TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,   -- 创建时间(UTC)
  updated_at   TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,   -- 更新时间(UTC,应用层维护)
  deleted      SMALLINT    NOT NULL DEFAULT 0,                    -- 逻辑删除 0=否 1=是
  version      INTEGER     NOT NULL DEFAULT 0,                    -- 乐观锁版本号
  CONSTRAINT pk_sys_notify_preference PRIMARY KEY (id),
  CONSTRAINT uk_sys_notify_pref UNIQUE (tenant_id, user_id, channel)
);

-- 文件结束: schema-postgresql.sql (共 119 张逻辑表, 不含 Flowable ACT_* 引擎表)
