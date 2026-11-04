-- =====================================================================
-- 智云 HR (hris-pro) 数据库基线 DDL - MySQL 8.0
-- 版本: v1.0  日期: 2026-10-15
-- 说明:
--   1) 本文件为 11 个服务逻辑库的字段级完整 DDL (不含 Flowable ACT_* 引擎表,
--      由 Flowable 7.2.x 按官方 schema 自动创建)。
--   2) 全局规范: 主键 BIGINT 雪花ID(应用层生成); 公共列 tenant_id/created_by/
--      updated_by/created_at/updated_at/deleted/version; 唯一约束 (tenant_id, xxx)
--      复合前缀; 金额 DECIMAL(18,4); 时间存 UTC 且为 DATETIME(3)。
--   3) 分片/广播/单表由 ShardingSphere 路由规则决定(逻辑表结构一致), 本 DDL 只定义
--      逻辑表结构; att_punch_record 与 sys_audit_log 的月度分区由平台调度任务维护。
--   4) 不建物理外键(分片+软删+跨库一致性), 逻辑外键在列注释标注。
-- =====================================================================

SET NAMES utf8mb4;

-- =====================================================================
-- 域 1: auth_db 认证授权
-- =====================================================================
CREATE DATABASE IF NOT EXISTS auth_db DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
USE auth_db;

-- 租户主档
CREATE TABLE auth_tenant (
  `id`            BIGINT       NOT NULL COMMENT '主键: 雪花ID(含租户位段)',
  `code`          VARCHAR(64)  NOT NULL COMMENT '租户编码(登录域/子域名标识)',
  `name`          VARCHAR(128) NOT NULL COMMENT '租户名称',
  `storage_mode`  VARCHAR(16)  NOT NULL DEFAULT 'SHARED' COMMENT '存储模式 SHARED=共享库 DEDICATED=专属数据源',
  `plan`          VARCHAR(32)  NOT NULL DEFAULT 'STARTER' COMMENT '订阅套餐',
  `status`        TINYINT      NOT NULL DEFAULT 1 COMMENT '状态 1=正常 0=停用 2=过期',
  `timezone`      VARCHAR(64)  NOT NULL DEFAULT 'Asia/Shanghai' COMMENT '租户时区(展示用)',
  `domain`        VARCHAR(128) DEFAULT NULL COMMENT '租户独立域名',
  `created_by`    BIGINT       NOT NULL DEFAULT 0 COMMENT '创建人ID,0=系统',
  `updated_by`    BIGINT       NOT NULL DEFAULT 0 COMMENT '更新人ID',
  `created_at`    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间(UTC)',
  `updated_at`    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '更新时间(UTC,应用层维护)',
  `deleted`       TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除 0=否 1=是',
  `version`       INT          NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_auth_tenant_code` (`code`),
  UNIQUE KEY `uk_auth_tenant_domain` (`domain`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='租户主档';

-- 用户账号(一人一账号, 离职禁用)
CREATE TABLE auth_user (
  `id`            BIGINT       NOT NULL COMMENT '主键: 雪花ID',
  `tenant_id`     BIGINT       NOT NULL COMMENT '租户ID',
  `employee_id`   BIGINT       NOT NULL COMMENT '员工ID(逻辑外键 emp_employee.id)',
  `username`      VARCHAR(64)  NOT NULL COMMENT '登录用户名',
  `pwd_hash`      VARCHAR(255) NOT NULL COMMENT '密码哈希(Argon2id)',
  `mfa_secret`    VARCHAR(64)  DEFAULT NULL COMMENT 'TOTP 密钥(启用MFA后写入,密文)',
  `status`        TINYINT      NOT NULL DEFAULT 1 COMMENT '状态 1=正常 0=禁用(离职联动)',
  `created_by`    BIGINT       NOT NULL DEFAULT 0 COMMENT '创建人ID,0=系统',
  `updated_by`    BIGINT       NOT NULL DEFAULT 0 COMMENT '更新人ID',
  `created_at`    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间(UTC)',
  `updated_at`    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '更新时间(UTC,应用层维护)',
  `deleted`       TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除 0=否 1=是',
  `version`       INT          NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_auth_user_username` (`tenant_id`, `username`),
  UNIQUE KEY `uk_auth_user_employee` (`tenant_id`, `employee_id`),
  KEY `idx_auth_user_status` (`tenant_id`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户账号';

-- 租户角色(RBAC)
CREATE TABLE auth_role (
  `id`            BIGINT       NOT NULL COMMENT '主键: 雪花ID',
  `tenant_id`     BIGINT       NOT NULL COMMENT '租户ID',
  `code`          VARCHAR(64)  NOT NULL COMMENT '角色编码(内置模板或自定义)',
  `name`          VARCHAR(64)  NOT NULL COMMENT '角色名称',
  `builtin`       TINYINT      NOT NULL DEFAULT 0 COMMENT '是否内置模板 1=内置 0=自定义',
  `created_by`    BIGINT       NOT NULL DEFAULT 0 COMMENT '创建人ID,0=系统',
  `updated_by`    BIGINT       NOT NULL DEFAULT 0 COMMENT '更新人ID',
  `created_at`    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间(UTC)',
  `updated_at`    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '更新时间(UTC,应用层维护)',
  `deleted`       TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除 0=否 1=是',
  `version`       INT          NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_auth_role_code` (`tenant_id`, `code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='租户角色';

-- 角色-权限点
CREATE TABLE auth_role_perm (
  `id`            BIGINT       NOT NULL COMMENT '主键: 雪花ID',
  `tenant_id`     BIGINT       NOT NULL COMMENT '租户ID',
  `role_id`       BIGINT       NOT NULL COMMENT '角色ID(逻辑外键 auth_role.id)',
  `perm_code`     VARCHAR(128) NOT NULL COMMENT '权限点编码(模块:资源:动作)',
  `created_by`    BIGINT       NOT NULL DEFAULT 0 COMMENT '创建人ID,0=系统',
  `updated_by`    BIGINT       NOT NULL DEFAULT 0 COMMENT '更新人ID',
  `created_at`    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间(UTC)',
  `updated_at`    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '更新时间(UTC,应用层维护)',
  `deleted`       TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除 0=否 1=是',
  `version`       INT          NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_auth_role_perm` (`role_id`, `perm_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='角色权限点';

-- 用户-角色
CREATE TABLE auth_user_role (
  `id`            BIGINT       NOT NULL COMMENT '主键: 雪花ID',
  `tenant_id`     BIGINT       NOT NULL COMMENT '租户ID',
  `user_id`       BIGINT       NOT NULL COMMENT '用户ID(逻辑外键 auth_user.id)',
  `role_id`       BIGINT       NOT NULL COMMENT '角色ID(逻辑外键 auth_role.id)',
  `created_by`    BIGINT       NOT NULL DEFAULT 0 COMMENT '创建人ID,0=系统',
  `updated_by`    BIGINT       NOT NULL DEFAULT 0 COMMENT '更新人ID',
  `created_at`    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间(UTC)',
  `updated_at`    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '更新时间(UTC,应用层维护)',
  `deleted`       TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除 0=否 1=是',
  `version`       INT          NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_auth_user_role` (`user_id`, `role_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户角色关联';

-- 数据权限规则
CREATE TABLE auth_data_scope (
  `id`            BIGINT       NOT NULL COMMENT '主键: 雪花ID',
  `tenant_id`     BIGINT       NOT NULL COMMENT '租户ID',
  `role_id`       BIGINT       NOT NULL COMMENT '角色ID(逻辑外键 auth_role.id)',
  `scope_type`    VARCHAR(16)  NOT NULL COMMENT '范围 SELF/DEPT/DEPT_SUB/CUSTOM/ALL',
  `dept_ids`      JSON         DEFAULT NULL COMMENT '自定义范围部门ID集合(CUSTOM时)',
  `created_by`    BIGINT       NOT NULL DEFAULT 0 COMMENT '创建人ID,0=系统',
  `updated_by`    BIGINT       NOT NULL DEFAULT 0 COMMENT '更新人ID',
  `created_at`    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间(UTC)',
  `updated_at`    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '更新时间(UTC,应用层维护)',
  `deleted`       TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除 0=否 1=是',
  `version`       INT          NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
  PRIMARY KEY (`id`),
  KEY `idx_auth_data_scope_role` (`tenant_id`, `role_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='数据权限规则';

-- OAuth2 客户端注册 (Spring Authorization Server 官方表, 平台级, 不带 tenant_id)
CREATE TABLE oauth2_registered_client (
  `id`                            VARCHAR(100)  NOT NULL COMMENT '注册ID',
  `client_id`                     VARCHAR(100)  NOT NULL COMMENT '客户端ID',
  `client_id_issued_at`           DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '客户端ID签发时间',
  `client_secret`                 VARCHAR(200)  DEFAULT NULL COMMENT '客户端密钥(密文)',
  `client_secret_expires_at`      DATETIME(3)   DEFAULT NULL COMMENT '客户端密钥过期时间',
  `client_name`                   VARCHAR(200)  NOT NULL COMMENT '客户端名称',
  `client_authentication_methods` VARCHAR(1000) NOT NULL COMMENT '认证方法列表',
  `authorization_grant_types`     VARCHAR(1000) NOT NULL COMMENT '授权类型列表',
  `redirect_uris`                 VARCHAR(1000) DEFAULT NULL COMMENT '重定向URI列表',
  `post_logout_redirect_uris`     VARCHAR(1000) DEFAULT NULL COMMENT '登出重定向URI列表',
  `scopes`                        VARCHAR(1000) NOT NULL COMMENT '授权范围列表',
  `client_settings`               VARCHAR(2000) NOT NULL COMMENT '客户端设置(JSON)',
  `token_settings`                VARCHAR(2000) NOT NULL COMMENT '令牌设置(JSON)',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='OAuth2客户端注册(Spring Authorization Server官方表)';

-- OAuth2 授权/token 存储 (官方表)
CREATE TABLE oauth2_authorization (
  `id`                            VARCHAR(100)  NOT NULL COMMENT '授权ID',
  `registered_client_id`          VARCHAR(100)  NOT NULL COMMENT '客户端注册ID',
  `principal_name`                VARCHAR(200)  NOT NULL COMMENT '主体名(用户)',
  `authorization_grant_type`      VARCHAR(100)  NOT NULL COMMENT '授权类型',
  `authorized_scopes`             VARCHAR(1000) DEFAULT NULL COMMENT '已授权范围',
  `attributes`                    BLOB          DEFAULT NULL COMMENT '附加属性(序列化)',
  `state`                         VARCHAR(500)  DEFAULT NULL COMMENT 'state 参数',
  `authorization_code_value`      BLOB          DEFAULT NULL COMMENT '授权码值',
  `authorization_code_issued_at`  DATETIME(3)   DEFAULT NULL COMMENT '授权码签发时间',
  `authorization_code_expires_at` DATETIME(3)   DEFAULT NULL COMMENT '授权码过期时间',
  `authorization_code_metadata`   BLOB          DEFAULT NULL COMMENT '授权码元数据',
  `access_token_value`            BLOB          DEFAULT NULL COMMENT '访问令牌值',
  `access_token_issued_at`        DATETIME(3)   DEFAULT NULL COMMENT '访问令牌签发时间',
  `access_token_expires_at`       DATETIME(3)   DEFAULT NULL COMMENT '访问令牌过期时间',
  `access_token_metadata`         BLOB          DEFAULT NULL COMMENT '访问令牌元数据',
  `access_token_type`             VARCHAR(100)  DEFAULT NULL COMMENT '访问令牌类型',
  `access_token_scopes`           VARCHAR(1000) DEFAULT NULL COMMENT '访问令牌范围',
  `oidc_id_token_value`           BLOB          DEFAULT NULL COMMENT 'OIDC ID令牌值',
  `oidc_id_token_issued_at`       DATETIME(3)   DEFAULT NULL COMMENT 'ID令牌签发时间',
  `oidc_id_token_expires_at`      DATETIME(3)   DEFAULT NULL COMMENT 'ID令牌过期时间',
  `oidc_id_token_metadata`        BLOB          DEFAULT NULL COMMENT 'ID令牌元数据',
  `refresh_token_value`           BLOB          DEFAULT NULL COMMENT '刷新令牌值',
  `refresh_token_issued_at`       DATETIME(3)   DEFAULT NULL COMMENT '刷新令牌签发时间',
  `refresh_token_expires_at`      DATETIME(3)   DEFAULT NULL COMMENT '刷新令牌过期时间',
  `refresh_token_metadata`        BLOB          DEFAULT NULL COMMENT '刷新令牌元数据',
  `user_code_value`               BLOB          DEFAULT NULL COMMENT '用户码值(设备流)',
  `user_code_issued_at`           DATETIME(3)   DEFAULT NULL COMMENT '用户码签发时间',
  `user_code_expires_at`          DATETIME(3)   DEFAULT NULL COMMENT '用户码过期时间',
  `user_code_metadata`            BLOB          DEFAULT NULL COMMENT '用户码元数据',
  `device_code_value`             BLOB          DEFAULT NULL COMMENT '设备码值',
  `device_code_issued_at`         DATETIME(3)   DEFAULT NULL COMMENT '设备码签发时间',
  `device_code_expires_at`        DATETIME(3)   DEFAULT NULL COMMENT '设备码过期时间',
  `device_code_metadata`          BLOB          DEFAULT NULL COMMENT '设备码元数据',
  PRIMARY KEY (`id`),
  KEY `idx_oauth2_auth_client_principal` (`registered_client_id`, `principal_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='OAuth2授权存储(Spring Authorization Server官方表)';

-- OAuth2 授权同意 (官方表)
CREATE TABLE oauth2_authorization_consent (
  `registered_client_id` VARCHAR(100)  NOT NULL COMMENT '客户端注册ID',
  `principal_name`       VARCHAR(200)  NOT NULL COMMENT '主体名(用户)',
  `authorities`          VARCHAR(1000) NOT NULL COMMENT '已同意权限列表',
  PRIMARY KEY (`registered_client_id`, `principal_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='OAuth2授权同意(Spring Authorization Server官方表)';

-- =====================================================================
-- 域 2: org_db 组织岗位
-- =====================================================================
CREATE DATABASE IF NOT EXISTS org_db DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
USE org_db;

-- 法人主体
CREATE TABLE org_legal_entity (
  `id`            BIGINT       NOT NULL COMMENT '主键: 雪花ID',
  `tenant_id`     BIGINT       NOT NULL COMMENT '租户ID',
  `name`          VARCHAR(128) NOT NULL COMMENT '法人名称',
  `uscc`          VARCHAR(32)  NOT NULL COMMENT '统一社会信用代码',
  `currency`      VARCHAR(8)   NOT NULL DEFAULT 'CNY' COMMENT '记账本位币',
  `status`        TINYINT      NOT NULL DEFAULT 1 COMMENT '状态 1=有效 0=失效',
  `created_by`    BIGINT       NOT NULL DEFAULT 0 COMMENT '创建人ID,0=系统',
  `updated_by`    BIGINT       NOT NULL DEFAULT 0 COMMENT '更新人ID',
  `created_at`    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间(UTC)',
  `updated_at`    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '更新时间(UTC,应用层维护)',
  `deleted`       TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除 0=否 1=是',
  `version`       INT          NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_org_legal_entity_uscc` (`tenant_id`, `uscc`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='法人主体';

-- 部门树(物化路径+版本化生效)
CREATE TABLE org_dept (
  `id`               BIGINT       NOT NULL COMMENT '主键: 雪花ID',
  `tenant_id`        BIGINT       NOT NULL COMMENT '租户ID',
  `parent_id`        BIGINT       NOT NULL DEFAULT 0 COMMENT '父部门ID,0=根',
  `path`             VARCHAR(255) NOT NULL COMMENT '物化路径(如 1.12.130)',
  `name`             VARCHAR(128) NOT NULL COMMENT '部门名称',
  `code`             VARCHAR(64)  NOT NULL COMMENT '部门编码',
  `manager_emp_id`   BIGINT       DEFAULT NULL COMMENT '部门负责人员工ID(逻辑外键 emp_employee.id)',
  `effective_from`   DATE         NOT NULL COMMENT '生效开始日',
  `effective_to`     DATE         DEFAULT NULL COMMENT '生效结束日(NULL=现行)',
  `status`           TINYINT      NOT NULL DEFAULT 1 COMMENT '状态 1=有效 0=失效',
  `created_by`       BIGINT       NOT NULL DEFAULT 0 COMMENT '创建人ID,0=系统',
  `updated_by`       BIGINT       NOT NULL DEFAULT 0 COMMENT '更新人ID',
  `created_at`       DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间(UTC)',
  `updated_at`       DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '更新时间(UTC,应用层维护)',
  `deleted`          TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除 0=否 1=是',
  `version`          INT          NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_org_dept_code` (`tenant_id`, `code`),
  KEY `idx_org_dept_parent` (`tenant_id`, `parent_id`),
  KEY `idx_org_dept_path` (`tenant_id`, `path`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='部门';

-- 岗位
CREATE TABLE org_position (
  `id`            BIGINT       NOT NULL COMMENT '主键: 雪花ID',
  `tenant_id`     BIGINT       NOT NULL COMMENT '租户ID',
  `name`          VARCHAR(64)  NOT NULL COMMENT '岗位名称',
  `code`          VARCHAR(64)  NOT NULL COMMENT '岗位编码',
  `status`        TINYINT      NOT NULL DEFAULT 1 COMMENT '状态 1=有效 0=失效',
  `created_by`    BIGINT       NOT NULL DEFAULT 0 COMMENT '创建人ID,0=系统',
  `updated_by`    BIGINT       NOT NULL DEFAULT 0 COMMENT '更新人ID',
  `created_at`    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间(UTC)',
  `updated_at`    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '更新时间(UTC,应用层维护)',
  `deleted`       TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除 0=否 1=是',
  `version`       INT          NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_org_position_code` (`tenant_id`, `code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='岗位';

-- 职务/职级序列
CREATE TABLE org_job (
  `id`            BIGINT       NOT NULL COMMENT '主键: 雪花ID',
  `tenant_id`     BIGINT       NOT NULL COMMENT '租户ID',
  `name`          VARCHAR(64)  NOT NULL COMMENT '职务名称',
  `code`          VARCHAR(64)  NOT NULL COMMENT '职务编码',
  `status`        TINYINT      NOT NULL DEFAULT 1 COMMENT '状态 1=有效 0=失效',
  `created_by`    BIGINT       NOT NULL DEFAULT 0 COMMENT '创建人ID,0=系统',
  `updated_by`    BIGINT       NOT NULL DEFAULT 0 COMMENT '更新人ID',
  `created_at`    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间(UTC)',
  `updated_at`    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '更新时间(UTC,应用层维护)',
  `deleted`       TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除 0=否 1=是',
  `version`       INT          NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_org_job_code` (`tenant_id`, `code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='职务';

-- 职级
CREATE TABLE org_grade (
  `id`            BIGINT       NOT NULL COMMENT '主键: 雪花ID',
  `tenant_id`     BIGINT       NOT NULL COMMENT '租户ID',
  `name`          VARCHAR(64)  NOT NULL COMMENT '职级名称',
  `code`          VARCHAR(64)  NOT NULL COMMENT '职级编码',
  `grade_band_id` BIGINT       DEFAULT NULL COMMENT '所属职级带ID(逻辑外键 org_grade_band.id)',
  `status`        TINYINT      NOT NULL DEFAULT 1 COMMENT '状态 1=有效 0=失效',
  `created_by`    BIGINT       NOT NULL DEFAULT 0 COMMENT '创建人ID,0=系统',
  `updated_by`    BIGINT       NOT NULL DEFAULT 0 COMMENT '更新人ID',
  `created_at`    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间(UTC)',
  `updated_at`    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '更新时间(UTC,应用层维护)',
  `deleted`       TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除 0=否 1=是',
  `version`       INT          NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_org_grade_code` (`tenant_id`, `code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='职级';

-- 职级带(广播表)
CREATE TABLE org_grade_band (
  `id`            BIGINT       NOT NULL COMMENT '主键: 雪花ID',
  `tenant_id`     BIGINT       NOT NULL COMMENT '租户ID',
  `name`          VARCHAR(64)  NOT NULL COMMENT '职级带名称',
  `code`          VARCHAR(64)  NOT NULL COMMENT '职级带编码',
  `min_grade`     VARCHAR(16)  DEFAULT NULL COMMENT '带内最低职级编码',
  `max_grade`     VARCHAR(16)  DEFAULT NULL COMMENT '带内最高职级编码',
  `created_by`    BIGINT       NOT NULL DEFAULT 0 COMMENT '创建人ID,0=系统',
  `updated_by`    BIGINT       NOT NULL DEFAULT 0 COMMENT '更新人ID',
  `created_at`    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间(UTC)',
  `updated_at`    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '更新时间(UTC,应用层维护)',
  `deleted`       TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除 0=否 1=是',
  `version`       INT          NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_org_grade_band_code` (`tenant_id`, `code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='职级带';

-- 编制
CREATE TABLE org_headcount (
  `id`            BIGINT         NOT NULL COMMENT '主键: 雪花ID',
  `tenant_id`     BIGINT         NOT NULL COMMENT '租户ID',
  `dept_id`       BIGINT         NOT NULL COMMENT '部门ID(逻辑外键 org_dept.id)',
  `position_id`   BIGINT         NOT NULL COMMENT '岗位ID(逻辑外键 org_position.id)',
  `period`        VARCHAR(16)    NOT NULL COMMENT '编制周期(如 2026Q1)',
  `quota`         INT            NOT NULL DEFAULT 0 COMMENT '编制数',
  `occupied`      INT            NOT NULL DEFAULT 0 COMMENT '已占用数',
  `created_by`    BIGINT         NOT NULL DEFAULT 0 COMMENT '创建人ID,0=系统',
  `updated_by`    BIGINT         NOT NULL DEFAULT 0 COMMENT '更新人ID',
  `created_at`    DATETIME(3)    NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间(UTC)',
  `updated_at`    DATETIME(3)    NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '更新时间(UTC,应用层维护)',
  `deleted`       TINYINT        NOT NULL DEFAULT 0 COMMENT '逻辑删除 0=否 1=是',
  `version`       INT            NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_org_headcount` (`tenant_id`, `dept_id`, `position_id`, `period`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='编制';

-- 编制占用流水
CREATE TABLE org_hc_occupancy (
  `id`             BIGINT       NOT NULL COMMENT '主键: 雪花ID',
  `tenant_id`      BIGINT       NOT NULL COMMENT '租户ID',
  `emp_id`         BIGINT       NOT NULL COMMENT '员工ID(逻辑外键 emp_employee.id)',
  `requisition_id` BIGINT       DEFAULT NULL COMMENT '招聘需求ID(逻辑外键 rcu_requisition.id)',
  `change_type`    VARCHAR(16)  NOT NULL COMMENT '变动类型 OCCUPY/RELEASE/TRANSFER',
  `dept_id`        BIGINT       NOT NULL COMMENT '部门ID(逻辑外键 org_dept.id)',
  `position_id`    BIGINT       NOT NULL COMMENT '岗位ID(逻辑外键 org_position.id)',
  `created_by`     BIGINT       NOT NULL DEFAULT 0 COMMENT '创建人ID,0=系统',
  `updated_by`     BIGINT       NOT NULL DEFAULT 0 COMMENT '更新人ID',
  `created_at`     DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间(UTC)',
  `updated_at`     DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '更新时间(UTC,应用层维护)',
  `deleted`        TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除 0=否 1=是',
  `version`        INT          NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
  PRIMARY KEY (`id`),
  KEY `idx_org_hc_occupancy_emp` (`tenant_id`, `emp_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='编制占用流水';

-- 部门历史快照
CREATE TABLE org_dept_history (
  `id`            BIGINT       NOT NULL COMMENT '主键: 雪花ID',
  `tenant_id`     BIGINT       NOT NULL COMMENT '租户ID',
  `dept_id`       BIGINT       NOT NULL COMMENT '部门ID(逻辑外键 org_dept.id)',
  `snapshot_date` DATE         NOT NULL COMMENT '快照日期',
  `snapshot`      JSON         NOT NULL COMMENT '部门快照(树/属性/负责人)',
  `created_by`    BIGINT       NOT NULL DEFAULT 0 COMMENT '创建人ID,0=系统',
  `updated_by`    BIGINT       NOT NULL DEFAULT 0 COMMENT '更新人ID',
  `created_at`    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间(UTC)',
  `updated_at`    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '更新时间(UTC,应用层维护)',
  `deleted`       TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除 0=否 1=是',
  `version`       INT          NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
  PRIMARY KEY (`id`),
  KEY `idx_org_dept_history` (`tenant_id`, `dept_id`, `snapshot_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='部门历史快照';

-- =====================================================================
-- 域 3: emp_db 员工 (全表按 tenant_id 分片, 绑定表组)
-- =====================================================================
CREATE DATABASE IF NOT EXISTS emp_db DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
USE emp_db;

-- 员工主档
CREATE TABLE emp_employee (
  `id`               BIGINT       NOT NULL COMMENT '主键: 雪花ID',
  `tenant_id`        BIGINT       NOT NULL COMMENT '租户ID',
  `emp_no`           VARCHAR(32)  NOT NULL COMMENT '员工工号',
  `name`             VARCHAR(64)  NOT NULL COMMENT '姓名',
  `pinyin_sort_key`  VARCHAR(128) DEFAULT NULL COMMENT '姓名拼音排序键(应用生成,规避排序规则差异)',
  `cert_type`        VARCHAR(16)  NOT NULL DEFAULT 'ID_CARD' COMMENT '证件类型',
  `cert_no_cipher`   VARCHAR(256) DEFAULT NULL COMMENT '证件号密文(AES-256-GCM,逻辑列 cert_no)',
  `cert_no_mask`     VARCHAR(64)  DEFAULT NULL COMMENT '证件号掩码(保留首尾)',
  `mobile_cipher`    VARCHAR(256) DEFAULT NULL COMMENT '手机号密文(逻辑列 mobile)',
  `mobile_mask`      VARCHAR(32)  DEFAULT NULL COMMENT '手机号掩码',
  `email`            VARCHAR(128) DEFAULT NULL COMMENT '邮箱',
  `gender`           VARCHAR(8)   DEFAULT NULL COMMENT '性别 M/F',
  `birth_date`       DATE         DEFAULT NULL COMMENT '出生日期',
  `status`           TINYINT      NOT NULL DEFAULT 0 COMMENT '员工状态机(8态,见03-02§4) 0=待入职 1=试用 2=在职 3=离职中 4=已离职 5=已取消 6=退休 7=黑名单',
  `blacklisted`      TINYINT      NOT NULL DEFAULT 0 COMMENT '是否黑名单 1=是 0=否',
  `created_by`       BIGINT       NOT NULL DEFAULT 0 COMMENT '创建人ID,0=系统',
  `updated_by`       BIGINT       NOT NULL DEFAULT 0 COMMENT '更新人ID',
  `created_at`       DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间(UTC)',
  `updated_at`       DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '更新时间(UTC,应用层维护)',
  `deleted`          TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除 0=否 1=是',
  `version`          INT          NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_emp_employee_no` (`tenant_id`, `emp_no`),
  KEY `idx_emp_employee_status` (`tenant_id`, `status`),
  KEY `idx_emp_employee_pinyin` (`tenant_id`, `pinyin_sort_key`),
  KEY `idx_emp_employee_cert` (`tenant_id`, `cert_no_cipher`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='员工主档';

-- 雇佣关系版本链(核心)
CREATE TABLE emp_employment (
  `id`               BIGINT       NOT NULL COMMENT '主键: 雪花ID',
  `tenant_id`        BIGINT       NOT NULL COMMENT '租户ID',
  `emp_id`           BIGINT       NOT NULL COMMENT '员工ID(逻辑外键 emp_employee.id)',
  `legal_entity_id`  BIGINT       NOT NULL COMMENT '法人ID(逻辑外键 org_legal_entity.id)',
  `dept_id`          BIGINT       NOT NULL COMMENT '部门ID(逻辑外键 org_dept.id)',
  `position_id`      BIGINT       NOT NULL COMMENT '岗位ID(逻辑外键 org_position.id)',
  `grade_id`         BIGINT       DEFAULT NULL COMMENT '职级ID(逻辑外键 org_grade.id)',
  `hire_type`        VARCHAR(16)  NOT NULL COMMENT '雇佣类型 FULLTIME/PARTTIME/INTERN/CONSULTANT',
  `supervisor_emp_id` BIGINT      DEFAULT NULL COMMENT '直属上级员工ID(逻辑外键 emp_employee.id)',
  `effective_from`   DATE         NOT NULL COMMENT '生效开始日',
  `effective_to`     DATE         DEFAULT NULL COMMENT '生效结束日(NULL=现行)',
  `status`           TINYINT      NOT NULL DEFAULT 1 COMMENT '状态 1=有效 0=失效',
  `created_by`       BIGINT       NOT NULL DEFAULT 0 COMMENT '创建人ID,0=系统',
  `updated_by`       BIGINT       NOT NULL DEFAULT 0 COMMENT '更新人ID',
  `created_at`       DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间(UTC)',
  `updated_at`       DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '更新时间(UTC,应用层维护)',
  `deleted`          TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除 0=否 1=是',
  `version`          INT          NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_emp_employment_eff` (`tenant_id`, `emp_id`, `effective_from`),
  KEY `idx_emp_employment_dept` (`tenant_id`, `dept_id`),
  KEY `idx_emp_employment_position` (`tenant_id`, `position_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='雇佣关系版本链';

-- 教育经历
CREATE TABLE emp_profile_education (
  `id`            BIGINT       NOT NULL COMMENT '主键: 雪花ID',
  `tenant_id`     BIGINT       NOT NULL COMMENT '租户ID',
  `employee_id`   BIGINT       NOT NULL COMMENT '员工ID(逻辑外键 emp_employee.id)',
  `school`        VARCHAR(128) NOT NULL COMMENT '学校名称',
  `major`         VARCHAR(128) DEFAULT NULL COMMENT '专业',
  `degree`        VARCHAR(16)  DEFAULT NULL COMMENT '学历',
  `start_date`    DATE         DEFAULT NULL COMMENT '开始日期',
  `end_date`      DATE         DEFAULT NULL COMMENT '结束日期',
  `created_by`    BIGINT       NOT NULL DEFAULT 0 COMMENT '创建人ID,0=系统',
  `updated_by`    BIGINT       NOT NULL DEFAULT 0 COMMENT '更新人ID',
  `created_at`    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间(UTC)',
  `updated_at`    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '更新时间(UTC,应用层维护)',
  `deleted`       TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除 0=否 1=是',
  `version`       INT          NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
  PRIMARY KEY (`id`),
  KEY `idx_emp_edu_employee` (`tenant_id`, `employee_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='教育经历';

-- 工作经历
CREATE TABLE emp_profile_work (
  `id`            BIGINT       NOT NULL COMMENT '主键: 雪花ID',
  `tenant_id`     BIGINT       NOT NULL COMMENT '租户ID',
  `employee_id`   BIGINT       NOT NULL COMMENT '员工ID(逻辑外键 emp_employee.id)',
  `company`       VARCHAR(128) NOT NULL COMMENT '公司名称',
  `position_name` VARCHAR(64)  DEFAULT NULL COMMENT '职位名称',
  `start_date`    DATE         DEFAULT NULL COMMENT '开始日期',
  `end_date`      DATE         DEFAULT NULL COMMENT '结束日期',
  `description`   VARCHAR(512) DEFAULT NULL COMMENT '工作描述',
  `created_by`    BIGINT       NOT NULL DEFAULT 0 COMMENT '创建人ID,0=系统',
  `updated_by`    BIGINT       NOT NULL DEFAULT 0 COMMENT '更新人ID',
  `created_at`    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间(UTC)',
  `updated_at`    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '更新时间(UTC,应用层维护)',
  `deleted`       TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除 0=否 1=是',
  `version`       INT          NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
  PRIMARY KEY (`id`),
  KEY `idx_emp_work_employee` (`tenant_id`, `employee_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='工作经历';

-- 家庭成员
CREATE TABLE emp_profile_family (
  `id`            BIGINT       NOT NULL COMMENT '主键: 雪花ID',
  `tenant_id`     BIGINT       NOT NULL COMMENT '租户ID',
  `employee_id`   BIGINT       NOT NULL COMMENT '员工ID(逻辑外键 emp_employee.id)',
  `name`          VARCHAR(64)  NOT NULL COMMENT '成员姓名',
  `relation`      VARCHAR(16)  NOT NULL COMMENT '关系(配偶/父母/子女)',
  `phone`         VARCHAR(32)  DEFAULT NULL COMMENT '联系电话',
  `company`       VARCHAR(128) DEFAULT NULL COMMENT '工作单位',
  `created_by`    BIGINT       NOT NULL DEFAULT 0 COMMENT '创建人ID,0=系统',
  `updated_by`    BIGINT       NOT NULL DEFAULT 0 COMMENT '更新人ID',
  `created_at`    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间(UTC)',
  `updated_at`    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '更新时间(UTC,应用层维护)',
  `deleted`       TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除 0=否 1=是',
  `version`       INT          NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
  PRIMARY KEY (`id`),
  KEY `idx_emp_family_employee` (`tenant_id`, `employee_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='家庭成员';

-- 紧急联系人
CREATE TABLE emp_profile_emergency (
  `id`            BIGINT       NOT NULL COMMENT '主键: 雪花ID',
  `tenant_id`     BIGINT       NOT NULL COMMENT '租户ID',
  `employee_id`   BIGINT       NOT NULL COMMENT '员工ID(逻辑外键 emp_employee.id)',
  `name`          VARCHAR(64)  NOT NULL COMMENT '联系人姓名',
  `relation`      VARCHAR(16)  NOT NULL COMMENT '关系',
  `phone`         VARCHAR(32)  NOT NULL COMMENT '联系电话',
  `address`       VARCHAR(255) DEFAULT NULL COMMENT '联系地址',
  `created_by`    BIGINT       NOT NULL DEFAULT 0 COMMENT '创建人ID,0=系统',
  `updated_by`    BIGINT       NOT NULL DEFAULT 0 COMMENT '更新人ID',
  `created_at`    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间(UTC)',
  `updated_at`    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '更新时间(UTC,应用层维护)',
  `deleted`       TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除 0=否 1=是',
  `version`       INT          NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
  PRIMARY KEY (`id`),
  KEY `idx_emp_emergency_employee` (`tenant_id`, `employee_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='紧急联系人';

-- 合同台账
CREATE TABLE emp_contract (
  `id`               BIGINT       NOT NULL COMMENT '主键: 雪花ID',
  `tenant_id`        BIGINT       NOT NULL COMMENT '租户ID',
  `emp_id`           BIGINT       NOT NULL COMMENT '员工ID(逻辑外键 emp_employee.id)',
  `contract_no`      VARCHAR(64)  NOT NULL COMMENT '合同编号',
  `type`             VARCHAR(16)  NOT NULL COMMENT '合同类型 FIXED/OPEN_ENDED/INTERN',
  `start_date`       DATE         NOT NULL COMMENT '合同开始日',
  `end_date`         DATE         DEFAULT NULL COMMENT '合同结束日(无固定期限为NULL)',
  `probation_end`    DATE         DEFAULT NULL COMMENT '试用期结束日',
  `renew_cnt`        INT          NOT NULL DEFAULT 0 COMMENT '续签次数',
  `file_id`          BIGINT       DEFAULT NULL COMMENT '合同附件ID(逻辑外键 sys_file_meta.id)',
  `status`           TINYINT      NOT NULL DEFAULT 1 COMMENT '状态 1=有效 0=终止',
  `created_by`       BIGINT       NOT NULL DEFAULT 0 COMMENT '创建人ID,0=系统',
  `updated_by`       BIGINT       NOT NULL DEFAULT 0 COMMENT '更新人ID',
  `created_at`       DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间(UTC)',
  `updated_at`       DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '更新时间(UTC,应用层维护)',
  `deleted`          TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除 0=否 1=是',
  `version`          INT          NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_emp_contract_no` (`tenant_id`, `contract_no`),
  KEY `idx_emp_contract_emp` (`tenant_id`, `emp_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='合同台账';

-- 生命周期单(入转调离)
CREATE TABLE emp_lifecycle_case (
  `id`            BIGINT       NOT NULL COMMENT '主键: 雪花ID',
  `tenant_id`     BIGINT       NOT NULL COMMENT '租户ID',
  `emp_id`        BIGINT       NOT NULL COMMENT '员工ID(逻辑外键 emp_employee.id)',
  `type`          VARCHAR(16)  NOT NULL COMMENT '类型 ONBOARD/REGULAR/TRANSFER/RESIGN',
  `biz_key`       VARCHAR(64)  NOT NULL COMMENT '业务单号(流程幂等键)',
  `payload`       JSON         DEFAULT NULL COMMENT '单据业务数据',
  `proc_inst_id`  VARCHAR(64)  DEFAULT NULL COMMENT '流程实例ID(逻辑外键 flow_ext_biz_relation.proc_inst_id)',
  `state`         VARCHAR(16)  NOT NULL DEFAULT 'DRAFT' COMMENT '状态 DRAFT/PROCESSING/DONE/CANCELLED',
  `effect_date`   DATE         DEFAULT NULL COMMENT '生效日期',
  `created_by`    BIGINT       NOT NULL DEFAULT 0 COMMENT '创建人ID,0=系统',
  `updated_by`    BIGINT       NOT NULL DEFAULT 0 COMMENT '更新人ID',
  `created_at`    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间(UTC)',
  `updated_at`    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '更新时间(UTC,应用层维护)',
  `deleted`       TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除 0=否 1=是',
  `version`       INT          NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_emp_lifecycle_biz` (`tenant_id`, `biz_key`),
  KEY `idx_emp_lifecycle_emp` (`tenant_id`, `emp_id`, `type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='生命周期单';

-- 待生效事务队列(幂等落账)
CREATE TABLE emp_pending_transaction (
  `id`            BIGINT       NOT NULL COMMENT '主键: 雪花ID',
  `tenant_id`     BIGINT       NOT NULL COMMENT '租户ID',
  `emp_id`        BIGINT       NOT NULL COMMENT '员工ID(逻辑外键 emp_employee.id)',
  `trans_no`      VARCHAR(64)  NOT NULL COMMENT '事务流水号(幂等键)',
  `trans_type`    VARCHAR(16)  NOT NULL COMMENT '事务类型 ONBOARD/REGULAR/TRANSFER/RESIGN/PROMOTE',
  `effect_date`   DATE         NOT NULL COMMENT '生效日期',
  `payload`       JSON         DEFAULT NULL COMMENT '事务载荷',
  `state`         VARCHAR(16)  NOT NULL DEFAULT 'PENDING' COMMENT '状态 PENDING/APPLIED/CANCELLED',
  `created_by`    BIGINT       NOT NULL DEFAULT 0 COMMENT '创建人ID,0=系统',
  `updated_by`    BIGINT       NOT NULL DEFAULT 0 COMMENT '更新人ID',
  `created_at`    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间(UTC)',
  `updated_at`    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '更新时间(UTC,应用层维护)',
  `deleted`       TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除 0=否 1=是',
  `version`       INT          NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_emp_pending_trans_no` (`tenant_id`, `trans_no`),
  KEY `idx_emp_pending_effect` (`tenant_id`, `effect_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='待生效事务队列';

-- 员工状态流水
CREATE TABLE emp_status_log (
  `id`            BIGINT       NOT NULL COMMENT '主键: 雪花ID',
  `tenant_id`     BIGINT       NOT NULL COMMENT '租户ID',
  `emp_id`        BIGINT       NOT NULL COMMENT '员工ID(逻辑外键 emp_employee.id)',
  `from_status`   TINYINT      DEFAULT NULL COMMENT '变更前状态',
  `to_status`     TINYINT      NOT NULL COMMENT '变更后状态',
  `reason`        VARCHAR(255) DEFAULT NULL COMMENT '变更原因',
  `created_by`    BIGINT       NOT NULL DEFAULT 0 COMMENT '创建人ID,0=系统',
  `updated_by`    BIGINT       NOT NULL DEFAULT 0 COMMENT '更新人ID',
  `created_at`    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间(UTC)',
  `updated_at`    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '更新时间(UTC,应用层维护)',
  `deleted`       TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除 0=否 1=是',
  `version`       INT          NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
  PRIMARY KEY (`id`),
  KEY `idx_emp_status_log_emp` (`tenant_id`, `emp_id`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='员工状态流水';

-- 字段变更审计
CREATE TABLE emp_change_audit (
  `id`            BIGINT       NOT NULL COMMENT '主键: 雪花ID',
  `tenant_id`     BIGINT       NOT NULL COMMENT '租户ID',
  `emp_id`        BIGINT       NOT NULL COMMENT '员工ID(逻辑外键 emp_employee.id)',
  `field_name`    VARCHAR(64)  NOT NULL COMMENT '变更字段',
  `old_value`     VARCHAR(512) DEFAULT NULL COMMENT '旧值',
  `new_value`     VARCHAR(512) DEFAULT NULL COMMENT '新值',
  `created_by`    BIGINT       NOT NULL DEFAULT 0 COMMENT '创建人ID(操作人)',
  `updated_by`    BIGINT       NOT NULL DEFAULT 0 COMMENT '更新人ID',
  `created_at`    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间(UTC)',
  `updated_at`    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '更新时间(UTC,应用层维护)',
  `deleted`       TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除 0=否 1=是',
  `version`       INT          NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
  PRIMARY KEY (`id`),
  KEY `idx_emp_change_audit` (`tenant_id`, `emp_id`, `field_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='字段变更审计';

-- =====================================================================
-- 域 4: att_db 假勤 (att_punch_record 分片 + 按月分区)
-- =====================================================================
CREATE DATABASE IF NOT EXISTS att_db DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
USE att_db;

-- 打卡记录(分片+月分区; 月度分区由平台调度任务维护, 此处给示例分区)
CREATE TABLE att_punch_record (
  `id`           BIGINT       NOT NULL COMMENT '主键: 雪花ID',
  `tenant_id`    BIGINT       NOT NULL COMMENT '租户ID',
  `emp_id`       BIGINT       NOT NULL COMMENT '员工ID(逻辑外键 emp_employee.id)',
  `punch_time`   DATETIME(3)  NOT NULL COMMENT '打卡时间(UTC,分区键)',
  `type`         VARCHAR(8)   NOT NULL COMMENT '打卡类型 IN/OUT',
  `source`       VARCHAR(16)  NOT NULL DEFAULT 'APP' COMMENT '打卡来源 APP/DEVICE/GPS',
  `location`     VARCHAR(255) DEFAULT NULL COMMENT '打卡地点',
  `device_id`    VARCHAR(64)  DEFAULT NULL COMMENT '设备标识',
  `att_group_id` BIGINT       DEFAULT NULL COMMENT '班组ID(逻辑外键 att_group.id)',
  `status`       TINYINT      NOT NULL DEFAULT 0 COMMENT '状态 0=正常 1=异常',
  `created_by`   BIGINT       NOT NULL DEFAULT 0 COMMENT '创建人ID,0=系统',
  `updated_by`   BIGINT       NOT NULL DEFAULT 0 COMMENT '更新人ID',
  `created_at`   DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间(UTC)',
  `updated_at`   DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '更新时间(UTC,应用层维护)',
  `deleted`      TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除 0=否 1=是',
  `version`      INT          NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
  PRIMARY KEY (`id`, `punch_time`),
  UNIQUE KEY `uk_att_punch_emp_time` (`tenant_id`, `emp_id`, `punch_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='打卡记录(分片+月分区)'
PARTITION BY RANGE COLUMNS(`punch_time`) (
  PARTITION p202601 VALUES LESS THAN ('2026-02-01'),
  PARTITION p202602 VALUES LESS THAN ('2026-03-01'),
  PARTITION p202603 VALUES LESS THAN ('2026-04-01'),
  PARTITION pmax VALUES LESS THAN (MAXVALUE)
);

-- 班组
CREATE TABLE att_group (
  `id`            BIGINT       NOT NULL COMMENT '主键: 雪花ID',
  `tenant_id`     BIGINT       NOT NULL COMMENT '租户ID',
  `name`          VARCHAR(64)  NOT NULL COMMENT '班组名称',
  `code`          VARCHAR(64)  NOT NULL COMMENT '班组编码',
  `leader_emp_id` BIGINT       DEFAULT NULL COMMENT '班组长员工ID(逻辑外键 emp_employee.id)',
  `created_by`    BIGINT       NOT NULL DEFAULT 0 COMMENT '创建人ID,0=系统',
  `updated_by`    BIGINT       NOT NULL DEFAULT 0 COMMENT '更新人ID',
  `created_at`    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间(UTC)',
  `updated_at`    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '更新时间(UTC,应用层维护)',
  `deleted`       TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除 0=否 1=是',
  `version`       INT          NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_att_group_code` (`tenant_id`, `code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='班组';

CREATE TABLE att_holiday (
  `id`            BIGINT       NOT NULL COMMENT '主键: 雪花ID',
  `tenant_id`     BIGINT       NOT NULL COMMENT '租户ID',
  `holiday_date`  DATE         NOT NULL COMMENT '日期',
  `name`          VARCHAR(64)  NOT NULL COMMENT '名称(如 春节/国庆调休)',
  `day_type`      VARCHAR(16)  NOT NULL COMMENT '日期类型: HOLIDAY=法定节假日(放假) WORKDAY=调班补班(上班)',
  `remark`        VARCHAR(255) DEFAULT NULL COMMENT '备注',
  `created_by`    BIGINT       NOT NULL DEFAULT 0 COMMENT '创建人ID,0=系统',
  `updated_by`    BIGINT       NOT NULL DEFAULT 0 COMMENT '更新人ID',
  `created_at`    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间(UTC)',
  `updated_at`    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '更新时间(UTC,应用层维护)',
  `deleted`       TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除 0=否 1=是',
  `version`       INT          NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_att_holiday_date` (`tenant_id`, `holiday_date`),
  KEY `idx_att_holiday_type` (`tenant_id`, `day_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='法定节假日与调班日';

-- 班次
CREATE TABLE att_shift (
  `id`            BIGINT       NOT NULL COMMENT '主键: 雪花ID',
  `tenant_id`     BIGINT       NOT NULL COMMENT '租户ID',
  `name`          VARCHAR(64)  NOT NULL COMMENT '班次名称',
  `code`          VARCHAR(64)  NOT NULL COMMENT '班次编码',
  `start_time`    TIME         NOT NULL COMMENT '上班时间',
  `end_time`      TIME         NOT NULL COMMENT '下班时间',
  `flexible`      TINYINT      NOT NULL DEFAULT 0 COMMENT '是否弹性 1=是 0=否',
  `created_by`    BIGINT       NOT NULL DEFAULT 0 COMMENT '创建人ID,0=系统',
  `updated_by`    BIGINT       NOT NULL DEFAULT 0 COMMENT '更新人ID',
  `created_at`    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间(UTC)',
  `updated_at`    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '更新时间(UTC,应用层维护)',
  `deleted`       TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除 0=否 1=是',
  `version`       INT          NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_att_shift_code` (`tenant_id`, `code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='班次';

-- 排班
CREATE TABLE att_shift_assignment (
  `id`            BIGINT       NOT NULL COMMENT '主键: 雪花ID',
  `tenant_id`     BIGINT       NOT NULL COMMENT '租户ID',
  `emp_id`        BIGINT       NOT NULL COMMENT '员工ID(逻辑外键 emp_employee.id)',
  `shift_id`      BIGINT       NOT NULL COMMENT '班次ID(逻辑外键 att_shift.id)',
  `work_date`     DATE         NOT NULL COMMENT '工作日',
  `created_by`    BIGINT       NOT NULL DEFAULT 0 COMMENT '创建人ID,0=系统',
  `updated_by`    BIGINT       NOT NULL DEFAULT 0 COMMENT '更新人ID',
  `created_at`    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间(UTC)',
  `updated_at`    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '更新时间(UTC,应用层维护)',
  `deleted`       TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除 0=否 1=是',
  `version`       INT          NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_att_assign_emp_date` (`tenant_id`, `emp_id`, `work_date`),
  KEY `idx_att_assign_shift` (`tenant_id`, `shift_id`, `work_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='排班';

-- 日考勤结果
CREATE TABLE att_daily_attendance (
  `id`            BIGINT       NOT NULL COMMENT '主键: 雪花ID',
  `tenant_id`     BIGINT       NOT NULL COMMENT '租户ID',
  `emp_id`        BIGINT       NOT NULL COMMENT '员工ID(逻辑外键 emp_employee.id)',
  `work_date`     DATE         NOT NULL COMMENT '工作日',
  `employment_id` BIGINT       NOT NULL COMMENT '雇佣关系ID(逻辑外键 emp_employment.id,日期归属版本)',
  `att_status`    VARCHAR(16)  NOT NULL DEFAULT 'NORMAL' COMMENT '出勤状态 NORMAL/LATE/EARLY/ABSENT/LEAVE',
  `punch_in`      DATETIME(3)  DEFAULT NULL COMMENT '实际打卡上班时间',
  `punch_out`     DATETIME(3)  DEFAULT NULL COMMENT '实际打卡下班时间',
  `leave_hours`   DECIMAL(6,2) NOT NULL DEFAULT 0 COMMENT '请假时长(小时)',
  `overtime_hours` DECIMAL(6,2) NOT NULL DEFAULT 0 COMMENT '加班时长(小时)',
  `created_by`    BIGINT       NOT NULL DEFAULT 0 COMMENT '创建人ID,0=系统',
  `updated_by`    BIGINT       NOT NULL DEFAULT 0 COMMENT '更新人ID',
  `created_at`    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间(UTC)',
  `updated_at`    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '更新时间(UTC,应用层维护)',
  `deleted`       TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除 0=否 1=是',
  `version`       INT          NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_att_daily_emp_date` (`tenant_id`, `emp_id`, `work_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='日考勤结果';

-- 考勤异常
CREATE TABLE att_exception (
  `id`            BIGINT       NOT NULL COMMENT '主键: 雪花ID',
  `tenant_id`     BIGINT       NOT NULL COMMENT '租户ID',
  `emp_id`        BIGINT       NOT NULL COMMENT '员工ID(逻辑外键 emp_employee.id)',
  `work_date`     DATE         NOT NULL COMMENT '工作日',
  `exception_type` VARCHAR(16) NOT NULL COMMENT '异常类型 LATE/EARLY/MISSING_PUNCH/ABSENT',
  `status`        VARCHAR(16)  NOT NULL DEFAULT 'OPEN' COMMENT '状态 OPEN/RESOLVED/IGNORED',
  `description`   VARCHAR(255) DEFAULT NULL COMMENT '异常说明',
  `created_by`    BIGINT       NOT NULL DEFAULT 0 COMMENT '创建人ID,0=系统',
  `updated_by`    BIGINT       NOT NULL DEFAULT 0 COMMENT '更新人ID',
  `created_at`    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间(UTC)',
  `updated_at`    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '更新时间(UTC,应用层维护)',
  `deleted`       TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除 0=否 1=是',
  `version`       INT          NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
  PRIMARY KEY (`id`),
  KEY `idx_att_exception_emp` (`tenant_id`, `emp_id`, `work_date`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='考勤异常';

-- 假种(规则 json)
CREATE TABLE att_leave_type (
  `id`            BIGINT       NOT NULL COMMENT '主键: 雪花ID',
  `tenant_id`     BIGINT       NOT NULL COMMENT '租户ID',
  `name`          VARCHAR(64)  NOT NULL COMMENT '假种名称',
  `code`          VARCHAR(64)  NOT NULL COMMENT '假种编码',
  `rules`         JSON         DEFAULT NULL COMMENT '额度/审批规则(json)',
  `created_by`    BIGINT       NOT NULL DEFAULT 0 COMMENT '创建人ID,0=系统',
  `updated_by`    BIGINT       NOT NULL DEFAULT 0 COMMENT '更新人ID',
  `created_at`    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间(UTC)',
  `updated_at`    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '更新时间(UTC,应用层维护)',
  `deleted`       TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除 0=否 1=是',
  `version`       INT          NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_att_leave_type_code` (`tenant_id`, `code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='假种';

-- 假期额度
CREATE TABLE att_leave_quota (
  `id`            BIGINT       NOT NULL COMMENT '主键: 雪花ID',
  `tenant_id`     BIGINT       NOT NULL COMMENT '租户ID',
  `emp_id`        BIGINT       NOT NULL COMMENT '员工ID(逻辑外键 emp_employee.id)',
  `leave_type_id` BIGINT       NOT NULL COMMENT '假种ID(逻辑外键 att_leave_type.id)',
  `period`        VARCHAR(16)  NOT NULL COMMENT '周期(如 2026)',
  `total_quota`   DECIMAL(6,2) NOT NULL DEFAULT 0 COMMENT '总额度(天)',
  `used_quota`    DECIMAL(6,2) NOT NULL DEFAULT 0 COMMENT '已用额度(天)',
  `created_by`    BIGINT       NOT NULL DEFAULT 0 COMMENT '创建人ID,0=系统',
  `updated_by`    BIGINT       NOT NULL DEFAULT 0 COMMENT '更新人ID',
  `created_at`    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间(UTC)',
  `updated_at`    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '更新时间(UTC,应用层维护)',
  `deleted`       TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除 0=否 1=是',
  `version`       INT          NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_att_leave_quota` (`tenant_id`, `emp_id`, `leave_type_id`, `period`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='假期额度';

-- 额度流水
CREATE TABLE att_leave_ledger (
  `id`            BIGINT       NOT NULL COMMENT '主键: 雪花ID',
  `tenant_id`     BIGINT       NOT NULL COMMENT '租户ID',
  `emp_id`        BIGINT       NOT NULL COMMENT '员工ID(逻辑外键 emp_employee.id)',
  `leave_type_id` BIGINT       NOT NULL COMMENT '假种ID(逻辑外键 att_leave_type.id)',
  `change_amount` DECIMAL(6,2) NOT NULL COMMENT '变动额度(天,正增负减)',
  `biz_ref`       VARCHAR(64)  DEFAULT NULL COMMENT '业务引用(请假单号)',
  `created_by`    BIGINT       NOT NULL DEFAULT 0 COMMENT '创建人ID,0=系统',
  `updated_by`    BIGINT       NOT NULL DEFAULT 0 COMMENT '更新人ID',
  `created_at`    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间(UTC)',
  `updated_at`    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '更新时间(UTC,应用层维护)',
  `deleted`       TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除 0=否 1=是',
  `version`       INT          NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
  PRIMARY KEY (`id`),
  KEY `idx_att_leave_ledger_emp` (`tenant_id`, `emp_id`, `leave_type_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='假期额度流水';

-- 请假单(关联 flow)
CREATE TABLE att_leave_request (
  `id`            BIGINT       NOT NULL COMMENT '主键: 雪花ID',
  `tenant_id`     BIGINT       NOT NULL COMMENT '租户ID',
  `emp_id`        BIGINT       NOT NULL COMMENT '员工ID(逻辑外键 emp_employee.id)',
  `leave_type_id` BIGINT       NOT NULL COMMENT '假种ID(逻辑外键 att_leave_type.id)',
  `biz_key`       VARCHAR(64)  NOT NULL COMMENT '业务单号(流程幂等键)',
  `start_time`    DATETIME(3)  NOT NULL COMMENT '请假开始时间(UTC)',
  `end_time`      DATETIME(3)  NOT NULL COMMENT '请假结束时间(UTC)',
  `days`          DECIMAL(6,2) NOT NULL COMMENT '请假天数',
  `reason`        VARCHAR(255) DEFAULT NULL COMMENT '请假事由',
  `status`        VARCHAR(16)  NOT NULL DEFAULT 'DRAFT' COMMENT '状态 DRAFT/PROCESSING/APPROVED/REJECTED/CANCELLED',
  `proc_inst_id`  VARCHAR(64)  DEFAULT NULL COMMENT '流程实例ID',
  `created_by`    BIGINT       NOT NULL DEFAULT 0 COMMENT '创建人ID,0=系统',
  `updated_by`    BIGINT       NOT NULL DEFAULT 0 COMMENT '更新人ID',
  `created_at`    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间(UTC)',
  `updated_at`    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '更新时间(UTC,应用层维护)',
  `deleted`       TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除 0=否 1=是',
  `version`       INT          NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_att_leave_biz` (`tenant_id`, `biz_key`),
  KEY `idx_att_leave_emp` (`tenant_id`, `emp_id`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='请假单';

-- 加班单
CREATE TABLE att_overtime_request (
  `id`            BIGINT       NOT NULL COMMENT '主键: 雪花ID',
  `tenant_id`     BIGINT       NOT NULL COMMENT '租户ID',
  `emp_id`        BIGINT       NOT NULL COMMENT '员工ID(逻辑外键 emp_employee.id)',
  `biz_key`       VARCHAR(64)  NOT NULL COMMENT '业务单号(流程幂等键)',
  `start_time`    DATETIME(3)  NOT NULL COMMENT '加班开始时间(UTC)',
  `end_time`      DATETIME(3)  NOT NULL COMMENT '加班结束时间(UTC)',
  `hours`         DECIMAL(6,2) NOT NULL COMMENT '加班时长(小时)',
  `reason`        VARCHAR(255) DEFAULT NULL COMMENT '加班事由',
  `status`        VARCHAR(16)  NOT NULL DEFAULT 'DRAFT' COMMENT '状态',
  `proc_inst_id`  VARCHAR(64)  DEFAULT NULL COMMENT '流程实例ID',
  `created_by`    BIGINT       NOT NULL DEFAULT 0 COMMENT '创建人ID,0=系统',
  `updated_by`    BIGINT       NOT NULL DEFAULT 0 COMMENT '更新人ID',
  `created_at`    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间(UTC)',
  `updated_at`    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '更新时间(UTC,应用层维护)',
  `deleted`       TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除 0=否 1=是',
  `version`       INT          NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_att_overtime_biz` (`tenant_id`, `biz_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='加班单';

-- 出差单
CREATE TABLE att_trip_request (
  `id`            BIGINT       NOT NULL COMMENT '主键: 雪花ID',
  `tenant_id`     BIGINT       NOT NULL COMMENT '租户ID',
  `emp_id`        BIGINT       NOT NULL COMMENT '员工ID(逻辑外键 emp_employee.id)',
  `biz_key`       VARCHAR(64)  NOT NULL COMMENT '业务单号(流程幂等键)',
  `start_time`    DATETIME(3)  NOT NULL COMMENT '出差开始时间(UTC)',
  `end_time`      DATETIME(3)  NOT NULL COMMENT '出差结束时间(UTC)',
  `destination`   VARCHAR(128) NOT NULL COMMENT '出差目的地',
  `reason`        VARCHAR(255) DEFAULT NULL COMMENT '出差事由',
  `status`        VARCHAR(16)  NOT NULL DEFAULT 'DRAFT' COMMENT '状态',
  `proc_inst_id`  VARCHAR(64)  DEFAULT NULL COMMENT '流程实例ID',
  `created_by`    BIGINT       NOT NULL DEFAULT 0 COMMENT '创建人ID,0=系统',
  `updated_by`    BIGINT       NOT NULL DEFAULT 0 COMMENT '更新人ID',
  `created_at`    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间(UTC)',
  `updated_at`    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '更新时间(UTC,应用层维护)',
  `deleted`       TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除 0=否 1=是',
  `version`       INT          NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_att_trip_biz` (`tenant_id`, `biz_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='出差单';

-- 补卡单
CREATE TABLE att_fix_request (
  `id`            BIGINT       NOT NULL COMMENT '主键: 雪花ID',
  `tenant_id`     BIGINT       NOT NULL COMMENT '租户ID',
  `emp_id`        BIGINT       NOT NULL COMMENT '员工ID(逻辑外键 emp_employee.id)',
  `biz_key`       VARCHAR(64)  NOT NULL COMMENT '业务单号(流程幂等键)',
  `work_date`     DATE         NOT NULL COMMENT '补卡日期',
  `fix_time`      DATETIME(3)  NOT NULL COMMENT '补卡时间(UTC)',
  `type`          VARCHAR(8)   NOT NULL COMMENT '补卡类型 IN/OUT',
  `reason`        VARCHAR(255) DEFAULT NULL COMMENT '补卡原因',
  `status`        VARCHAR(16)  NOT NULL DEFAULT 'DRAFT' COMMENT '状态',
  `proc_inst_id`  VARCHAR(64)  DEFAULT NULL COMMENT '流程实例ID',
  `created_by`    BIGINT       NOT NULL DEFAULT 0 COMMENT '创建人ID,0=系统',
  `updated_by`    BIGINT       NOT NULL DEFAULT 0 COMMENT '更新人ID',
  `created_at`    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间(UTC)',
  `updated_at`    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '更新时间(UTC,应用层维护)',
  `deleted`       TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除 0=否 1=是',
  `version`       INT          NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_att_fix_biz` (`tenant_id`, `biz_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='补卡单';

-- 薪酬取数周期
CREATE TABLE att_payroll_cycle (
  `id`            BIGINT       NOT NULL COMMENT '主键: 雪花ID',
  `tenant_id`     BIGINT       NOT NULL COMMENT '租户ID',
  `period_code`   VARCHAR(16)  NOT NULL COMMENT '周期编码(如 2026-01)',
  `start_date`    DATE         NOT NULL COMMENT '周期开始日',
  `end_date`      DATE         NOT NULL COMMENT '周期结束日',
  `status`        VARCHAR(16)  NOT NULL DEFAULT 'OPEN' COMMENT '状态 OPEN/CLOSED',
  `created_by`    BIGINT       NOT NULL DEFAULT 0 COMMENT '创建人ID,0=系统',
  `updated_by`    BIGINT       NOT NULL DEFAULT 0 COMMENT '更新人ID',
  `created_at`    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间(UTC)',
  `updated_at`    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '更新时间(UTC,应用层维护)',
  `deleted`       TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除 0=否 1=是',
  `version`       INT          NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_att_payroll_cycle` (`tenant_id`, `period_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='薪酬取数周期';

-- 月结批次
CREATE TABLE att_monthly_closing (
  `id`            BIGINT       NOT NULL COMMENT '主键: 雪花ID',
  `tenant_id`     BIGINT       NOT NULL COMMENT '租户ID',
  `period_code`   VARCHAR(16)  NOT NULL COMMENT '周期编码(如 2026-01)',
  `status`        VARCHAR(16)  NOT NULL DEFAULT 'INIT' COMMENT '状态 INIT/RUNNING/DONE/FAILED',
  `closed_at`     DATETIME(3)  DEFAULT NULL COMMENT '月结完成时间(UTC)',
  `created_by`    BIGINT       NOT NULL DEFAULT 0 COMMENT '创建人ID,0=系统',
  `updated_by`    BIGINT       NOT NULL DEFAULT 0 COMMENT '更新人ID',
  `created_at`    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间(UTC)',
  `updated_at`    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '更新时间(UTC,应用层维护)',
  `deleted`       TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除 0=否 1=是',
  `version`       INT          NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_att_closing_period` (`tenant_id`, `period_code`),
  KEY `idx_att_closing_status` (`tenant_id`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='月结批次';

-- 月结调整
CREATE TABLE att_closing_adjustment (
  `id`            BIGINT       NOT NULL COMMENT '主键: 雪花ID',
  `tenant_id`     BIGINT       NOT NULL COMMENT '租户ID',
  `emp_id`        BIGINT       NOT NULL COMMENT '员工ID(逻辑外键 emp_employee.id)',
  `closing_id`    BIGINT       NOT NULL COMMENT '月结批次ID(逻辑外键 att_monthly_closing.id)',
  `adjust_type`   VARCHAR(16)  NOT NULL COMMENT '调整类型',
  `amount`        DECIMAL(6,2) NOT NULL DEFAULT 0 COMMENT '调整量',
  `reason`        VARCHAR(255) DEFAULT NULL COMMENT '调整原因',
  `created_by`    BIGINT       NOT NULL DEFAULT 0 COMMENT '创建人ID,0=系统',
  `updated_by`    BIGINT       NOT NULL DEFAULT 0 COMMENT '更新人ID',
  `created_at`    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间(UTC)',
  `updated_at`    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '更新时间(UTC,应用层维护)',
  `deleted`       TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除 0=否 1=是',
  `version`       INT          NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
  PRIMARY KEY (`id`),
  KEY `idx_att_closing_adj` (`tenant_id`, `emp_id`, `closing_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='月结调整';

-- =====================================================================
-- 域 5: rcu_db 招聘
-- =====================================================================
CREATE DATABASE IF NOT EXISTS rcu_db DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
USE rcu_db;

-- 招聘需求
CREATE TABLE rcu_requisition (
  `id`            BIGINT       NOT NULL COMMENT '主键: 雪花ID',
  `tenant_id`     BIGINT       NOT NULL COMMENT '租户ID',
  `req_no`        VARCHAR(64)  NOT NULL COMMENT '需求编号',
  `dept_id`       BIGINT       NOT NULL COMMENT '部门ID(逻辑外键 org_dept.id)',
  `position_id`   BIGINT       NOT NULL COMMENT '岗位ID(逻辑外键 org_position.id)',
  `headcount`     INT          NOT NULL DEFAULT 1 COMMENT '招聘人数',
  `reason`        VARCHAR(255) DEFAULT NULL COMMENT '需求原因',
  `status`        VARCHAR(16)  NOT NULL DEFAULT 'DRAFT' COMMENT '状态 DRAFT/APPROVING/OPEN/CLOSED',
  `created_by`    BIGINT       NOT NULL DEFAULT 0 COMMENT '创建人ID,0=系统',
  `updated_by`    BIGINT       NOT NULL DEFAULT 0 COMMENT '更新人ID',
  `created_at`    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间(UTC)',
  `updated_at`    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '更新时间(UTC,应用层维护)',
  `deleted`       TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除 0=否 1=是',
  `version`       INT          NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_rcu_requisition_no` (`tenant_id`, `req_no`),
  KEY `idx_rcu_requisition_dept` (`tenant_id`, `dept_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='招聘需求';

-- 职位
CREATE TABLE rcu_job_opening (
  `id`              BIGINT       NOT NULL COMMENT '主键: 雪花ID',
  `tenant_id`       BIGINT       NOT NULL COMMENT '租户ID',
  `requisition_id`  BIGINT       DEFAULT NULL COMMENT '需求ID(逻辑外键 rcu_requisition.id)',
  `code`            VARCHAR(64)  NOT NULL COMMENT '职位编码',
  `title`           VARCHAR(128) NOT NULL COMMENT '职位名称',
  `description`     TEXT         DEFAULT NULL COMMENT '职位描述',
  `status`          VARCHAR(16)  NOT NULL DEFAULT 'DRAFT' COMMENT '状态 DRAFT/PUBLISHED/CLOSED',
  `publish_at`      DATETIME(3)  DEFAULT NULL COMMENT '发布时间(UTC)',
  `created_by`      BIGINT       NOT NULL DEFAULT 0 COMMENT '创建人ID,0=系统',
  `updated_by`      BIGINT       NOT NULL DEFAULT 0 COMMENT '更新人ID',
  `created_at`      DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间(UTC)',
  `updated_at`      DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '更新时间(UTC,应用层维护)',
  `deleted`         TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除 0=否 1=是',
  `version`         INT          NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_rcu_opening_code` (`tenant_id`, `code`),
  KEY `idx_rcu_opening_status` (`tenant_id`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='职位';

-- 招聘渠道
CREATE TABLE rcu_channel (
  `id`            BIGINT       NOT NULL COMMENT '主键: 雪花ID',
  `tenant_id`     BIGINT       NOT NULL COMMENT '租户ID',
  `name`          VARCHAR(64)  NOT NULL COMMENT '渠道名称',
  `code`          VARCHAR(64)  NOT NULL COMMENT '渠道编码',
  `created_by`    BIGINT       NOT NULL DEFAULT 0 COMMENT '创建人ID,0=系统',
  `updated_by`    BIGINT       NOT NULL DEFAULT 0 COMMENT '更新人ID',
  `created_at`    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间(UTC)',
  `updated_at`    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '更新时间(UTC,应用层维护)',
  `deleted`       TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除 0=否 1=是',
  `version`       INT          NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_rcu_channel_code` (`tenant_id`, `code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='招聘渠道';

-- 候选人
CREATE TABLE rcu_candidate (
  `id`            BIGINT       NOT NULL COMMENT '主键: 雪花ID',
  `tenant_id`     BIGINT       NOT NULL COMMENT '租户ID',
  `name`          VARCHAR(64)  NOT NULL COMMENT '候选人姓名',
  `mobile_cipher` VARCHAR(256) DEFAULT NULL COMMENT '手机号密文(逻辑列 mobile)',
  `mobile_mask`   VARCHAR(32)  DEFAULT NULL COMMENT '手机号掩码',
  `email`         VARCHAR(128) DEFAULT NULL COMMENT '邮箱',
  `status`        VARCHAR(16)  NOT NULL DEFAULT 'NEW' COMMENT '状态 NEW/SCREENING/INTERVIEWING/OFFERED/HIRED/REJECTED',
  `created_by`    BIGINT       NOT NULL DEFAULT 0 COMMENT '创建人ID,0=系统',
  `updated_by`    BIGINT       NOT NULL DEFAULT 0 COMMENT '更新人ID',
  `created_at`    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间(UTC)',
  `updated_at`    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '更新时间(UTC,应用层维护)',
  `deleted`       TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除 0=否 1=是',
  `version`       INT          NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
  PRIMARY KEY (`id`),
  KEY `idx_rcu_candidate_status` (`tenant_id`, `status`),
  KEY `idx_rcu_candidate_email` (`tenant_id`, `email`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='候选人';

-- 简历
CREATE TABLE rcu_resume (
  `id`            BIGINT       NOT NULL COMMENT '主键: 雪花ID',
  `tenant_id`     BIGINT       NOT NULL COMMENT '租户ID',
  `candidate_id`  BIGINT       NOT NULL COMMENT '候选人ID(逻辑外键 rcu_candidate.id)',
  `file_id`       BIGINT       DEFAULT NULL COMMENT '简历附件ID(逻辑外键 sys_file_meta.id)',
  `parse_result`  JSON         DEFAULT NULL COMMENT '简历解析结果(json)',
  `created_by`    BIGINT       NOT NULL DEFAULT 0 COMMENT '创建人ID,0=系统',
  `updated_by`    BIGINT       NOT NULL DEFAULT 0 COMMENT '更新人ID',
  `created_at`    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间(UTC)',
  `updated_at`    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '更新时间(UTC,应用层维护)',
  `deleted`       TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除 0=否 1=是',
  `version`       INT          NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
  PRIMARY KEY (`id`),
  KEY `idx_rcu_resume_candidate` (`tenant_id`, `candidate_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='简历';

-- 投递
CREATE TABLE rcu_application (
  `id`            BIGINT       NOT NULL COMMENT '主键: 雪花ID',
  `tenant_id`     BIGINT       NOT NULL COMMENT '租户ID',
  `candidate_id`  BIGINT       NOT NULL COMMENT '候选人ID(逻辑外键 rcu_candidate.id)',
  `opening_id`    BIGINT       NOT NULL COMMENT '职位ID(逻辑外键 rcu_job_opening.id)',
  `channel_id`    BIGINT       DEFAULT NULL COMMENT '渠道ID(逻辑外键 rcu_channel.id)',
  `stage`         VARCHAR(16)  NOT NULL DEFAULT 'APPLIED' COMMENT '阶段 APPLIED/SCREEN/INTERVIEW/OFFER/HIRED',
  `status`        VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE' COMMENT '状态 ACTIVE/REJECTED/WITHDRAWN',
  `created_by`    BIGINT       NOT NULL DEFAULT 0 COMMENT '创建人ID,0=系统',
  `updated_by`    BIGINT       NOT NULL DEFAULT 0 COMMENT '更新人ID',
  `created_at`    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间(UTC)',
  `updated_at`    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '更新时间(UTC,应用层维护)',
  `deleted`       TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除 0=否 1=是',
  `version`       INT          NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_rcu_application` (`tenant_id`, `candidate_id`, `opening_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='投递';

-- 阶段流转日志
CREATE TABLE rcu_stage_log (
  `id`             BIGINT       NOT NULL COMMENT '主键: 雪花ID',
  `tenant_id`      BIGINT       NOT NULL COMMENT '租户ID',
  `application_id` BIGINT       NOT NULL COMMENT '投递ID(逻辑外键 rcu_application.id)',
  `from_stage`     VARCHAR(16)  DEFAULT NULL COMMENT '原阶段',
  `to_stage`       VARCHAR(16)  NOT NULL COMMENT '新阶段',
  `remark`         VARCHAR(255) DEFAULT NULL COMMENT '备注',
  `created_by`     BIGINT       NOT NULL DEFAULT 0 COMMENT '创建人ID(操作人)',
  `updated_by`     BIGINT       NOT NULL DEFAULT 0 COMMENT '更新人ID',
  `created_at`     DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间(UTC)',
  `updated_at`     DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '更新时间(UTC,应用层维护)',
  `deleted`        TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除 0=否 1=是',
  `version`        INT          NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
  PRIMARY KEY (`id`),
  KEY `idx_rcu_stage_log_app` (`tenant_id`, `application_id`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='阶段流转日志';

-- 面试安排
CREATE TABLE rcu_interview (
  `id`             BIGINT       NOT NULL COMMENT '主键: 雪花ID',
  `tenant_id`      BIGINT       NOT NULL COMMENT '租户ID',
  `application_id` BIGINT       NOT NULL COMMENT '投递ID(逻辑外键 rcu_application.id)',
  `round`          INT          NOT NULL DEFAULT 1 COMMENT '面试轮次',
  `planned_at`     DATETIME(3)  DEFAULT NULL COMMENT '计划面试时间(UTC)',
  `interviewer_ids` JSON        DEFAULT NULL COMMENT '面试官ID集合',
  `status`         VARCHAR(16)  NOT NULL DEFAULT 'SCHEDULED' COMMENT '状态 SCHEDULED/DONE/CANCELLED',
  `created_by`     BIGINT       NOT NULL DEFAULT 0 COMMENT '创建人ID,0=系统',
  `updated_by`     BIGINT       NOT NULL DEFAULT 0 COMMENT '更新人ID',
  `created_at`     DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间(UTC)',
  `updated_at`     DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '更新时间(UTC,应用层维护)',
  `deleted`        TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除 0=否 1=是',
  `version`        INT          NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
  PRIMARY KEY (`id`),
  KEY `idx_rcu_interview_app` (`tenant_id`, `application_id`, `planned_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='面试安排';

-- 面试评价
CREATE TABLE rcu_interview_eval (
  `id`            BIGINT       NOT NULL COMMENT '主键: 雪花ID',
  `tenant_id`     BIGINT       NOT NULL COMMENT '租户ID',
  `interview_id`  BIGINT       NOT NULL COMMENT '面试ID(逻辑外键 rcu_interview.id)',
  `evaluator_id`  BIGINT       NOT NULL COMMENT '评价人用户ID(逻辑外键 auth_user.id)',
  `score`         INT          DEFAULT NULL COMMENT '评分',
  `comment`       VARCHAR(512) DEFAULT NULL COMMENT '评价意见',
  `pass`          TINYINT      DEFAULT NULL COMMENT '是否通过 1=通过 0=不通过',
  `created_by`    BIGINT       NOT NULL DEFAULT 0 COMMENT '创建人ID,0=系统',
  `updated_by`    BIGINT       NOT NULL DEFAULT 0 COMMENT '更新人ID',
  `created_at`    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间(UTC)',
  `updated_at`    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '更新时间(UTC,应用层维护)',
  `deleted`       TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除 0=否 1=是',
  `version`       INT          NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
  PRIMARY KEY (`id`),
  KEY `idx_rcu_interview_eval` (`tenant_id`, `interview_id`, `evaluator_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='面试评价';

-- Offer
CREATE TABLE rcu_offer (
  `id`               BIGINT       NOT NULL COMMENT '主键: 雪花ID',
  `tenant_id`        BIGINT       NOT NULL COMMENT '租户ID',
  `application_id`   BIGINT       NOT NULL COMMENT '投递ID(逻辑外键 rcu_application.id)',
  `offer_no`         VARCHAR(64)  NOT NULL COMMENT 'Offer编号',
  `salary_band_check` TINYINT     DEFAULT NULL COMMENT '薪资带校验结果 1=通过 0=超带',
  `salary_amount_cipher` VARCHAR(256) DEFAULT NULL COMMENT 'Offer薪资密文(逻辑列 salary_amount)',
  `salary_amount_mask`   VARCHAR(64)  DEFAULT NULL COMMENT 'Offer薪资掩码',
  `status`           VARCHAR(16)  NOT NULL DEFAULT 'DRAFT' COMMENT '状态 DRAFT/SENT/ACCEPTED/REJECTED',
  `proc_inst_id`     VARCHAR(64)  DEFAULT NULL COMMENT '流程实例ID',
  `created_by`       BIGINT       NOT NULL DEFAULT 0 COMMENT '创建人ID,0=系统',
  `updated_by`       BIGINT       NOT NULL DEFAULT 0 COMMENT '更新人ID',
  `created_at`       DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间(UTC)',
  `updated_at`       DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '更新时间(UTC,应用层维护)',
  `deleted`          TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除 0=否 1=是',
  `version`          INT          NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_rcu_offer_no` (`tenant_id`, `offer_no`),
  KEY `idx_rcu_offer_app` (`tenant_id`, `application_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Offer';

-- 人才库标签
CREATE TABLE rcu_talent_pool_tag (
  `id`            BIGINT       NOT NULL COMMENT '主键: 雪花ID',
  `tenant_id`     BIGINT       NOT NULL COMMENT '租户ID',
  `candidate_id`  BIGINT       NOT NULL COMMENT '候选人ID(逻辑外键 rcu_candidate.id)',
  `tag`           VARCHAR(64)  NOT NULL COMMENT '标签',
  `created_by`    BIGINT       NOT NULL DEFAULT 0 COMMENT '创建人ID,0=系统',
  `updated_by`    BIGINT       NOT NULL DEFAULT 0 COMMENT '更新人ID',
  `created_at`    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间(UTC)',
  `updated_at`    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '更新时间(UTC,应用层维护)',
  `deleted`       TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除 0=否 1=是',
  `version`       INT          NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_rcu_talent_tag` (`tenant_id`, `candidate_id`, `tag`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='人才库标签';

-- =====================================================================
-- 域 6: cmp_db 薪酬
-- =====================================================================
CREATE DATABASE IF NOT EXISTS cmp_db DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
USE cmp_db;

-- 薪酬项(公式 json 版本化)
CREATE TABLE cmp_pay_item (
  `id`            BIGINT       NOT NULL COMMENT '主键: 雪花ID',
  `tenant_id`     BIGINT       NOT NULL COMMENT '租户ID',
  `name`          VARCHAR(64)  NOT NULL COMMENT '薪酬项名称',
  `code`          VARCHAR(64)  NOT NULL COMMENT '薪酬项编码',
  `version`       INT          NOT NULL DEFAULT 1 COMMENT '版本号',
  `formula`       JSON         DEFAULT NULL COMMENT '计算公式(json)',
  `status`        TINYINT      NOT NULL DEFAULT 1 COMMENT '状态 1=启用 0=停用',
  `created_by`    BIGINT       NOT NULL DEFAULT 0 COMMENT '创建人ID,0=系统',
  `updated_by`    BIGINT       NOT NULL DEFAULT 0 COMMENT '更新人ID',
  `created_at`    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间(UTC)',
  `updated_at`    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '更新时间(UTC,应用层维护)',
  `deleted`       TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除 0=否 1=是',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_cmp_pay_item` (`tenant_id`, `code`, `version`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='薪酬项';

-- 薪资组
CREATE TABLE cmp_pay_group (
  `id`            BIGINT       NOT NULL COMMENT '主键: 雪花ID',
  `tenant_id`     BIGINT       NOT NULL COMMENT '租户ID',
  `name`          VARCHAR(64)  NOT NULL COMMENT '薪资组名称',
  `code`          VARCHAR(64)  NOT NULL COMMENT '薪资组编码',
  `pay_cycle`     VARCHAR(16)  NOT NULL DEFAULT 'MONTHLY' COMMENT '发薪周期 MONTHLY/BIWEEKLY',
  `created_by`    BIGINT       NOT NULL DEFAULT 0 COMMENT '创建人ID,0=系统',
  `updated_by`    BIGINT       NOT NULL DEFAULT 0 COMMENT '更新人ID',
  `created_at`    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间(UTC)',
  `updated_at`    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '更新时间(UTC,应用层维护)',
  `deleted`       TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除 0=否 1=是',
  `version`       INT          NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_cmp_pay_group_code` (`tenant_id`, `code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='薪资组';

-- 薪级
CREATE TABLE cmp_pay_grade (
  `id`            BIGINT       NOT NULL COMMENT '主键: 雪花ID',
  `tenant_id`     BIGINT       NOT NULL COMMENT '租户ID',
  `name`          VARCHAR(64)  NOT NULL COMMENT '薪级名称',
  `code`          VARCHAR(64)  NOT NULL COMMENT '薪级编码',
  `min_salary`    DECIMAL(18,4) DEFAULT NULL COMMENT '下限',
  `max_salary`    DECIMAL(18,4) DEFAULT NULL COMMENT '上限',
  `created_by`    BIGINT       NOT NULL DEFAULT 0 COMMENT '创建人ID,0=系统',
  `updated_by`    BIGINT       NOT NULL DEFAULT 0 COMMENT '更新人ID',
  `created_at`    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间(UTC)',
  `updated_at`    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '更新时间(UTC,应用层维护)',
  `deleted`       TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除 0=否 1=是',
  `version`       INT          NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_cmp_pay_grade_code` (`tenant_id`, `code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='薪级';

-- 员工定薪(生效区间版本, 金额加密)
CREATE TABLE cmp_employee_pay (
  `id`                   BIGINT       NOT NULL COMMENT '主键: 雪花ID',
  `tenant_id`            BIGINT       NOT NULL COMMENT '租户ID',
  `emp_id`               BIGINT       NOT NULL COMMENT '员工ID(逻辑外键 emp_employee.id)',
  `pay_group_id`         BIGINT       NOT NULL COMMENT '薪资组ID(逻辑外键 cmp_pay_group.id)',
  `pay_grade_id`         BIGINT       DEFAULT NULL COMMENT '薪级ID(逻辑外键 cmp_pay_grade.id)',
  `effective_from`       DATE         NOT NULL COMMENT '生效开始日',
  `effective_to`         DATE         DEFAULT NULL COMMENT '生效结束日(NULL=现行)',
  `salary_amount_cipher` VARCHAR(256) DEFAULT NULL COMMENT '定薪金额密文(逻辑列 salary_amount)',
  `salary_amount_mask`   VARCHAR(64)  DEFAULT NULL COMMENT '定薪金额掩码',
  `bank_account_cipher`  VARCHAR(256) DEFAULT NULL COMMENT '银行账号密文(逻辑列 bank_account)',
  `bank_account_mask`    VARCHAR(64)  DEFAULT NULL COMMENT '银行账号掩码',
  `created_by`           BIGINT       NOT NULL DEFAULT 0 COMMENT '创建人ID,0=系统',
  `updated_by`           BIGINT       NOT NULL DEFAULT 0 COMMENT '更新人ID',
  `created_at`           DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间(UTC)',
  `updated_at`           DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '更新时间(UTC,应用层维护)',
  `deleted`              TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除 0=否 1=是',
  `version`              INT          NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_cmp_employee_pay` (`tenant_id`, `emp_id`, `effective_from`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='员工定薪';

-- 社保公积金档案
CREATE TABLE cmp_social_fund_profile (
  `id`             BIGINT       NOT NULL COMMENT '主键: 雪花ID',
  `tenant_id`      BIGINT       NOT NULL COMMENT '租户ID',
  `emp_id`         BIGINT       NOT NULL COMMENT '员工ID(逻辑外键 emp_employee.id)',
  `effective_from` DATE         NOT NULL COMMENT '生效开始日',
  `effective_to`   DATE         DEFAULT NULL COMMENT '生效结束日',
  `fund_type`      VARCHAR(16)  NOT NULL COMMENT '类型 SOCIAL/HOUSING_FUND',
  `base_amount`    DECIMAL(18,4) NOT NULL DEFAULT 0 COMMENT '缴纳基数',
  `created_by`     BIGINT       NOT NULL DEFAULT 0 COMMENT '创建人ID,0=系统',
  `updated_by`     BIGINT       NOT NULL DEFAULT 0 COMMENT '更新人ID',
  `created_at`     DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间(UTC)',
  `updated_at`     DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '更新时间(UTC,应用层维护)',
  `deleted`        TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除 0=否 1=是',
  `version`        INT          NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_cmp_social_fund` (`tenant_id`, `emp_id`, `fund_type`, `effective_from`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='社保公积金档案';

-- 专项附加扣除(加密)
CREATE TABLE cmp_tax_deduction (
  `id`                     BIGINT       NOT NULL COMMENT '主键: 雪花ID',
  `tenant_id`              BIGINT       NOT NULL COMMENT '租户ID',
  `emp_id`                 BIGINT       NOT NULL COMMENT '员工ID(逻辑外键 emp_employee.id)',
  `effective_from`         DATE         NOT NULL COMMENT '生效开始日',
  `effective_to`           DATE         DEFAULT NULL COMMENT '生效结束日',
  `deduction_amount_cipher` VARCHAR(256) DEFAULT NULL COMMENT '专项附加扣除金额密文(逻辑列 deduction_amount)',
  `deduction_amount_mask`   VARCHAR(64)  DEFAULT NULL COMMENT '专项附加扣除金额掩码',
  `created_by`             BIGINT       NOT NULL DEFAULT 0 COMMENT '创建人ID,0=系统',
  `updated_by`             BIGINT       NOT NULL DEFAULT 0 COMMENT '更新人ID',
  `created_at`             DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间(UTC)',
  `updated_at`             DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '更新时间(UTC,应用层维护)',
  `deleted`                TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除 0=否 1=是',
  `version`                INT          NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_cmp_tax_deduction` (`tenant_id`, `emp_id`, `effective_from`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='专项附加扣除';

-- 算薪批次(状态机)
CREATE TABLE cmp_payroll_run (
  `id`            BIGINT       NOT NULL COMMENT '主键: 雪花ID',
  `tenant_id`     BIGINT       NOT NULL COMMENT '租户ID',
  `period_code`   VARCHAR(16)  NOT NULL COMMENT '周期编码(如 2026-01)',
  `pay_group_id`  BIGINT       NOT NULL COMMENT '薪资组ID(逻辑外键 cmp_pay_group.id)',
  `status`        VARCHAR(16)  NOT NULL DEFAULT 'INIT' COMMENT '状态 INIT/TRIAL/CALC/REVIEW/APPROVE/PAID/LOCK',
  `calc_by`       BIGINT       DEFAULT NULL COMMENT '计算人ID',
  `review_by`     BIGINT       DEFAULT NULL COMMENT '复核人ID',
  `created_by`    BIGINT       NOT NULL DEFAULT 0 COMMENT '创建人ID,0=系统',
  `updated_by`    BIGINT       NOT NULL DEFAULT 0 COMMENT '更新人ID',
  `created_at`    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间(UTC)',
  `updated_at`    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '更新时间(UTC,应用层维护)',
  `deleted`       TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除 0=否 1=是',
  `version`       INT          NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_cmp_run_period_group` (`tenant_id`, `period_code`, `pay_group_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='算薪批次';

-- 算薪明细(run+emp, 输入快照 json, 金额加密)
CREATE TABLE cmp_payroll_detail (
  `id`                BIGINT       NOT NULL COMMENT '主键: 雪花ID',
  `tenant_id`         BIGINT       NOT NULL COMMENT '租户ID',
  `run_id`            BIGINT       NOT NULL COMMENT '算薪批次ID(逻辑外键 cmp_payroll_run.id)',
  `emp_id`            BIGINT       NOT NULL COMMENT '员工ID(逻辑外键 emp_employee.id)',
  `input_snapshot`    JSON         DEFAULT NULL COMMENT '算薪输入快照(json,可复算)',
  `amount_cipher`     VARCHAR(256) DEFAULT NULL COMMENT '应发金额密文(逻辑列 amount)',
  `amount_mask`       VARCHAR(64)  DEFAULT NULL COMMENT '应发金额掩码',
  `net_amount_cipher` VARCHAR(256) DEFAULT NULL COMMENT '实发金额密文(逻辑列 net_amount)',
  `net_amount_mask`   VARCHAR(64)  DEFAULT NULL COMMENT '实发金额掩码',
  `created_by`        BIGINT       NOT NULL DEFAULT 0 COMMENT '创建人ID,0=系统',
  `updated_by`        BIGINT       NOT NULL DEFAULT 0 COMMENT '更新人ID',
  `created_at`        DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间(UTC)',
  `updated_at`        DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '更新时间(UTC,应用层维护)',
  `deleted`           TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除 0=否 1=是',
  `version`           INT          NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_cmp_detail_run_emp` (`tenant_id`, `run_id`, `emp_id`),
  KEY `idx_cmp_detail_run` (`tenant_id`, `run_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='算薪明细';

-- 算薪调整
CREATE TABLE cmp_payroll_adjustment (
  `id`            BIGINT       NOT NULL COMMENT '主键: 雪花ID',
  `tenant_id`     BIGINT       NOT NULL COMMENT '租户ID',
  `run_id`        BIGINT       NOT NULL COMMENT '算薪批次ID(逻辑外键 cmp_payroll_run.id)',
  `emp_id`        BIGINT       NOT NULL COMMENT '员工ID(逻辑外键 emp_employee.id)',
  `adjust_type`   VARCHAR(16)  NOT NULL COMMENT '调整类型',
  `amount`        DECIMAL(18,4) NOT NULL DEFAULT 0 COMMENT '调整金额',
  `reason`        VARCHAR(255) DEFAULT NULL COMMENT '调整原因',
  `created_by`    BIGINT       NOT NULL DEFAULT 0 COMMENT '创建人ID,0=系统',
  `updated_by`    BIGINT       NOT NULL DEFAULT 0 COMMENT '更新人ID',
  `created_at`    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间(UTC)',
  `updated_at`    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '更新时间(UTC,应用层维护)',
  `deleted`       TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除 0=否 1=是',
  `version`       INT          NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
  PRIMARY KEY (`id`),
  KEY `idx_cmp_adjustment` (`tenant_id`, `run_id`, `emp_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='算薪调整';

-- 发放指令
CREATE TABLE cmp_payment_instruction (
  `id`            BIGINT       NOT NULL COMMENT '主键: 雪花ID',
  `tenant_id`     BIGINT       NOT NULL COMMENT '租户ID',
  `run_id`        BIGINT       NOT NULL COMMENT '算薪批次ID(逻辑外键 cmp_payroll_run.id)',
  `emp_id`        BIGINT       NOT NULL COMMENT '员工ID(逻辑外键 emp_employee.id)',
  `pay_status`    VARCHAR(16)  NOT NULL DEFAULT 'PENDING' COMMENT '发放状态 PENDING/SUCCESS/FAILED',
  `paid_at`       DATETIME(3)  DEFAULT NULL COMMENT '发放时间(UTC)',
  `created_by`    BIGINT       NOT NULL DEFAULT 0 COMMENT '创建人ID,0=系统',
  `updated_by`    BIGINT       NOT NULL DEFAULT 0 COMMENT '更新人ID',
  `created_at`    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间(UTC)',
  `updated_at`    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '更新时间(UTC,应用层维护)',
  `deleted`       TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除 0=否 1=是',
  `version`       INT          NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_cmp_payment_run_emp` (`tenant_id`, `run_id`, `emp_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='发放指令';

-- 工资条(密文 + 发放状态)
CREATE TABLE cmp_payslip (
  `id`              BIGINT       NOT NULL COMMENT '主键: 雪花ID',
  `tenant_id`       BIGINT       NOT NULL COMMENT '租户ID',
  `run_id`          BIGINT       NOT NULL COMMENT '算薪批次ID(逻辑外键 cmp_payroll_run.id)',
  `emp_id`          BIGINT       NOT NULL COMMENT '员工ID(逻辑外键 emp_employee.id)',
  `net_pay_cipher`  VARCHAR(256) DEFAULT NULL COMMENT '实发净额密文(逻辑列 net_pay)',
  `net_pay_mask`    VARCHAR(64)  DEFAULT NULL COMMENT '实发净额掩码',
  `send_status`     VARCHAR(16)  NOT NULL DEFAULT 'PENDING' COMMENT '发放状态 PENDING/SENT/VIEWED',
  `sent_at`         DATETIME(3)  DEFAULT NULL COMMENT '发送时间(UTC)',
  `created_by`      BIGINT       NOT NULL DEFAULT 0 COMMENT '创建人ID,0=系统',
  `updated_by`      BIGINT       NOT NULL DEFAULT 0 COMMENT '更新人ID',
  `created_at`      DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间(UTC)',
  `updated_at`      DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '更新时间(UTC,应用层维护)',
  `deleted`         TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除 0=否 1=是',
  `version`         INT          NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_cmp_payslip_run_emp` (`tenant_id`, `run_id`, `emp_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='工资条';

-- 导入批次
CREATE TABLE cmp_import_batch (
  `id`            BIGINT       NOT NULL COMMENT '主键: 雪花ID',
  `tenant_id`     BIGINT       NOT NULL COMMENT '租户ID',
  `batch_no`      VARCHAR(64)  NOT NULL COMMENT '批次号',
  `import_type`   VARCHAR(16)  NOT NULL COMMENT '导入类型',
  `total_rows`    INT          NOT NULL DEFAULT 0 COMMENT '总行数',
  `success_rows`  INT          NOT NULL DEFAULT 0 COMMENT '成功行数',
  `fail_rows`     INT          NOT NULL DEFAULT 0 COMMENT '失败行数',
  `status`        VARCHAR(16)  NOT NULL DEFAULT 'RUNNING' COMMENT '状态 RUNNING/DONE/FAILED',
  `created_by`    BIGINT       NOT NULL DEFAULT 0 COMMENT '创建人ID,0=系统',
  `updated_by`    BIGINT       NOT NULL DEFAULT 0 COMMENT '更新人ID',
  `created_at`    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间(UTC)',
  `updated_at`    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '更新时间(UTC,应用层维护)',
  `deleted`       TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除 0=否 1=是',
  `version`       INT          NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_cmp_import_batch` (`tenant_id`, `batch_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='导入批次';

-- =====================================================================
-- 域 7: prf_db 绩效与培训
-- =====================================================================
CREATE DATABASE IF NOT EXISTS prf_db DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
USE prf_db;

-- 考核方案
CREATE TABLE prf_plan (
  `id`            BIGINT       NOT NULL COMMENT '主键: 雪花ID',
  `tenant_id`     BIGINT       NOT NULL COMMENT '租户ID',
  `name`          VARCHAR(64)  NOT NULL COMMENT '方案名称',
  `code`          VARCHAR(64)  NOT NULL COMMENT '方案编码',
  `status`        TINYINT      NOT NULL DEFAULT 1 COMMENT '状态 1=启用 0=停用',
  `created_by`    BIGINT       NOT NULL DEFAULT 0 COMMENT '创建人ID,0=系统',
  `updated_by`    BIGINT       NOT NULL DEFAULT 0 COMMENT '更新人ID',
  `created_at`    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间(UTC)',
  `updated_at`    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '更新时间(UTC,应用层维护)',
  `deleted`       TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除 0=否 1=是',
  `version`       INT          NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_prf_plan_code` (`tenant_id`, `code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='考核方案';

-- 考核周期
CREATE TABLE prf_cycle (
  `id`            BIGINT       NOT NULL COMMENT '主键: 雪花ID',
  `tenant_id`     BIGINT       NOT NULL COMMENT '租户ID',
  `plan_id`       BIGINT       NOT NULL COMMENT '方案ID(逻辑外键 prf_plan.id)',
  `period_code`   VARCHAR(16)  NOT NULL COMMENT '周期编码(如 2026H1)',
  `start_date`    DATE         NOT NULL COMMENT '周期开始日',
  `end_date`      DATE         NOT NULL COMMENT '周期结束日',
  `status`        VARCHAR(16)  NOT NULL DEFAULT 'DRAFT' COMMENT '状态 DRAFT/ACTIVE/CALIBRATING/PUBLISHED/CLOSED',
  `created_by`    BIGINT       NOT NULL DEFAULT 0 COMMENT '创建人ID,0=系统',
  `updated_by`    BIGINT       NOT NULL DEFAULT 0 COMMENT '更新人ID',
  `created_at`    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间(UTC)',
  `updated_at`    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '更新时间(UTC,应用层维护)',
  `deleted`       TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除 0=否 1=是',
  `version`       INT          NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_prf_cycle` (`tenant_id`, `plan_id`, `period_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='考核周期';

-- 目标(KPI 权重)
CREATE TABLE prf_goal (
  `id`            BIGINT       NOT NULL COMMENT '主键: 雪花ID',
  `tenant_id`     BIGINT       NOT NULL COMMENT '租户ID',
  `cycle_id`      BIGINT       NOT NULL COMMENT '周期ID(逻辑外键 prf_cycle.id)',
  `emp_id`        BIGINT       NOT NULL COMMENT '员工ID(逻辑外键 emp_employee.id)',
  `title`         VARCHAR(128) NOT NULL COMMENT '目标标题',
  `weight`        DECIMAL(5,2) NOT NULL DEFAULT 0 COMMENT '权重(%)',
  `target_value`  VARCHAR(255) DEFAULT NULL COMMENT '目标值',
  `result_value`  VARCHAR(255) DEFAULT NULL COMMENT '完成值',
  `created_by`    BIGINT       NOT NULL DEFAULT 0 COMMENT '创建人ID,0=系统',
  `updated_by`    BIGINT       NOT NULL DEFAULT 0 COMMENT '更新人ID',
  `created_at`    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间(UTC)',
  `updated_at`    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '更新时间(UTC,应用层维护)',
  `deleted`       TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除 0=否 1=是',
  `version`       INT          NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
  PRIMARY KEY (`id`),
  KEY `idx_prf_goal` (`tenant_id`, `cycle_id`, `emp_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='考核目标';

-- OKR 目标
CREATE TABLE prf_okr_objective (
  `id`            BIGINT       NOT NULL COMMENT '主键: 雪花ID',
  `tenant_id`     BIGINT       NOT NULL COMMENT '租户ID',
  `cycle_id`      BIGINT       NOT NULL COMMENT '周期ID(逻辑外键 prf_cycle.id)',
  `emp_id`        BIGINT       NOT NULL COMMENT '员工ID(逻辑外键 emp_employee.id)',
  `objective`     VARCHAR(255) NOT NULL COMMENT '目标O',
  `key_results`   JSON         DEFAULT NULL COMMENT '关键结果KR列表(json)',
  `created_by`    BIGINT       NOT NULL DEFAULT 0 COMMENT '创建人ID,0=系统',
  `updated_by`    BIGINT       NOT NULL DEFAULT 0 COMMENT '更新人ID',
  `created_at`    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间(UTC)',
  `updated_at`    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '更新时间(UTC,应用层维护)',
  `deleted`       TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除 0=否 1=是',
  `version`       INT          NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
  PRIMARY KEY (`id`),
  KEY `idx_prf_okr` (`tenant_id`, `cycle_id`, `emp_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='OKR目标';

-- 评估任务
CREATE TABLE prf_eval_task (
  `id`            BIGINT       NOT NULL COMMENT '主键: 雪花ID',
  `tenant_id`     BIGINT       NOT NULL COMMENT '租户ID',
  `cycle_id`      BIGINT       NOT NULL COMMENT '周期ID(逻辑外键 prf_cycle.id)',
  `emp_id`        BIGINT       NOT NULL COMMENT '被评估人员工ID(逻辑外键 emp_employee.id)',
  `evaluator_role` VARCHAR(16) NOT NULL COMMENT '评价人角色 SELF/SUPERVISOR/PEER/SUBORDINATE',
  `status`        VARCHAR(16)  NOT NULL DEFAULT 'PENDING' COMMENT '状态 PENDING/DONE',
  `created_by`    BIGINT       NOT NULL DEFAULT 0 COMMENT '创建人ID,0=系统',
  `updated_by`    BIGINT       NOT NULL DEFAULT 0 COMMENT '更新人ID',
  `created_at`    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间(UTC)',
  `updated_at`    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '更新时间(UTC,应用层维护)',
  `deleted`       TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除 0=否 1=是',
  `version`       INT          NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_prf_eval_task` (`tenant_id`, `cycle_id`, `emp_id`, `evaluator_role`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='评估任务';

-- 评价(评价人环 json)
CREATE TABLE prf_evaluation (
  `id`            BIGINT       NOT NULL COMMENT '主键: 雪花ID',
  `tenant_id`     BIGINT       NOT NULL COMMENT '租户ID',
  `task_id`       BIGINT       NOT NULL COMMENT '评估任务ID(逻辑外键 prf_eval_task.id)',
  `evaluator_id`  BIGINT       NOT NULL COMMENT '评价人用户ID(逻辑外键 auth_user.id)',
  `scores`        JSON         DEFAULT NULL COMMENT '评分明细(json)',
  `comment`       VARCHAR(512) DEFAULT NULL COMMENT '评语',
  `created_by`    BIGINT       NOT NULL DEFAULT 0 COMMENT '创建人ID,0=系统',
  `updated_by`    BIGINT       NOT NULL DEFAULT 0 COMMENT '更新人ID',
  `created_at`    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间(UTC)',
  `updated_at`    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '更新时间(UTC,应用层维护)',
  `deleted`       TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除 0=否 1=是',
  `version`       INT          NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_prf_evaluation` (`tenant_id`, `task_id`, `evaluator_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='评价';

-- 强制分布规则
CREATE TABLE prf_forced_rule (
  `id`            BIGINT       NOT NULL COMMENT '主键: 雪花ID',
  `tenant_id`     BIGINT       NOT NULL COMMENT '租户ID',
  `plan_id`       BIGINT       NOT NULL COMMENT '方案ID(逻辑外键 prf_plan.id)',
  `grade`         VARCHAR(16)  NOT NULL COMMENT '等级(如 A/B/C/D)',
  `ratio`         DECIMAL(5,2) NOT NULL COMMENT '比例(%)',
  `created_by`    BIGINT       NOT NULL DEFAULT 0 COMMENT '创建人ID,0=系统',
  `updated_by`    BIGINT       NOT NULL DEFAULT 0 COMMENT '更新人ID',
  `created_at`    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间(UTC)',
  `updated_at`    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '更新时间(UTC,应用层维护)',
  `deleted`       TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除 0=否 1=是',
  `version`       INT          NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_prf_forced_rule` (`tenant_id`, `plan_id`, `grade`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='强制分布规则';

-- 校准会议
CREATE TABLE prf_calibration_session (
  `id`            BIGINT       NOT NULL COMMENT '主键: 雪花ID',
  `tenant_id`     BIGINT       NOT NULL COMMENT '租户ID',
  `cycle_id`      BIGINT       NOT NULL COMMENT '周期ID(逻辑外键 prf_cycle.id)',
  `dept_id`       BIGINT       NOT NULL COMMENT '部门ID(逻辑外键 org_dept.id)',
  `status`        VARCHAR(16)  NOT NULL DEFAULT 'DRAFT' COMMENT '状态 DRAFT/IN_PROGRESS/DONE',
  `created_by`    BIGINT       NOT NULL DEFAULT 0 COMMENT '创建人ID,0=系统',
  `updated_by`    BIGINT       NOT NULL DEFAULT 0 COMMENT '更新人ID',
  `created_at`    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间(UTC)',
  `updated_at`    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '更新时间(UTC,应用层维护)',
  `deleted`       TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除 0=否 1=是',
  `version`       INT          NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_prf_calibration` (`tenant_id`, `cycle_id`, `dept_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='校准会议';

-- 校准快照
CREATE TABLE prf_calibration_snapshot (
  `id`            BIGINT       NOT NULL COMMENT '主键: 雪花ID',
  `tenant_id`     BIGINT       NOT NULL COMMENT '租户ID',
  `session_id`    BIGINT       NOT NULL COMMENT '校准会议ID(逻辑外键 prf_calibration_session.id)',
  `snapshot`      JSON         NOT NULL COMMENT '校准快照(九宫格/调整结果)',
  `created_by`    BIGINT       NOT NULL DEFAULT 0 COMMENT '创建人ID,0=系统',
  `updated_by`    BIGINT       NOT NULL DEFAULT 0 COMMENT '更新人ID',
  `created_at`    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间(UTC)',
  `updated_at`    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '更新时间(UTC,应用层维护)',
  `deleted`       TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除 0=否 1=是',
  `version`       INT          NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
  PRIMARY KEY (`id`),
  KEY `idx_prf_calib_snapshot` (`tenant_id`, `session_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='校准快照';

-- 申诉(关联 flow)
CREATE TABLE prf_appeal (
  `id`            BIGINT       NOT NULL COMMENT '主键: 雪花ID',
  `tenant_id`     BIGINT       NOT NULL COMMENT '租户ID',
  `emp_id`        BIGINT       NOT NULL COMMENT '员工ID(逻辑外键 emp_employee.id)',
  `cycle_id`      BIGINT       NOT NULL COMMENT '周期ID(逻辑外键 prf_cycle.id)',
  `biz_key`       VARCHAR(64)  NOT NULL COMMENT '业务单号(流程幂等键)',
  `reason`        VARCHAR(255) DEFAULT NULL COMMENT '申诉理由',
  `status`        VARCHAR(16)  NOT NULL DEFAULT 'DRAFT' COMMENT '状态 DRAFT/PROCESSING/APPROVED/REJECTED',
  `proc_inst_id`  VARCHAR(64)  DEFAULT NULL COMMENT '流程实例ID',
  `created_by`    BIGINT       NOT NULL DEFAULT 0 COMMENT '创建人ID,0=系统',
  `updated_by`    BIGINT       NOT NULL DEFAULT 0 COMMENT '更新人ID',
  `created_at`    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间(UTC)',
  `updated_at`    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '更新时间(UTC,应用层维护)',
  `deleted`       TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除 0=否 1=是',
  `version`       INT          NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_prf_appeal_biz` (`tenant_id`, `biz_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='绩效申诉';

-- 培训计划
CREATE TABLE prf_train_plan (
  `id`            BIGINT       NOT NULL COMMENT '主键: 雪花ID',
  `tenant_id`     BIGINT       NOT NULL COMMENT '租户ID',
  `name`          VARCHAR(128) NOT NULL COMMENT '计划名称',
  `code`          VARCHAR(64)  NOT NULL COMMENT '计划编码',
  `start_date`    DATE         DEFAULT NULL COMMENT '计划开始日',
  `end_date`      DATE         DEFAULT NULL COMMENT '计划结束日',
  `created_by`    BIGINT       NOT NULL DEFAULT 0 COMMENT '创建人ID,0=系统',
  `updated_by`    BIGINT       NOT NULL DEFAULT 0 COMMENT '更新人ID',
  `created_at`    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间(UTC)',
  `updated_at`    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '更新时间(UTC,应用层维护)',
  `deleted`       TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除 0=否 1=是',
  `version`       INT          NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_prf_train_plan_code` (`tenant_id`, `code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='培训计划';

-- 课程
CREATE TABLE prf_course (
  `id`            BIGINT       NOT NULL COMMENT '主键: 雪花ID',
  `tenant_id`     BIGINT       NOT NULL COMMENT '租户ID',
  `name`          VARCHAR(128) NOT NULL COMMENT '课程名称',
  `code`          VARCHAR(64)  NOT NULL COMMENT '课程编码',
  `hours`         DECIMAL(6,2) NOT NULL DEFAULT 0 COMMENT '课程学时',
  `created_by`    BIGINT       NOT NULL DEFAULT 0 COMMENT '创建人ID,0=系统',
  `updated_by`    BIGINT       NOT NULL DEFAULT 0 COMMENT '更新人ID',
  `created_at`    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间(UTC)',
  `updated_at`    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '更新时间(UTC,应用层维护)',
  `deleted`       TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除 0=否 1=是',
  `version`       INT          NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_prf_course_code` (`tenant_id`, `code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='课程';

-- 讲师
CREATE TABLE prf_instructor (
  `id`            BIGINT       NOT NULL COMMENT '主键: 雪花ID',
  `tenant_id`     BIGINT       NOT NULL COMMENT '租户ID',
  `emp_id`        BIGINT       NOT NULL COMMENT '讲师员工ID(逻辑外键 emp_employee.id)',
  `created_by`    BIGINT       NOT NULL DEFAULT 0 COMMENT '创建人ID,0=系统',
  `updated_by`    BIGINT       NOT NULL DEFAULT 0 COMMENT '更新人ID',
  `created_at`    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间(UTC)',
  `updated_at`    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '更新时间(UTC,应用层维护)',
  `deleted`       TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除 0=否 1=是',
  `version`       INT          NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_prf_instructor` (`tenant_id`, `emp_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='讲师';

-- 开班场次
CREATE TABLE prf_class_session (
  `id`            BIGINT       NOT NULL COMMENT '主键: 雪花ID',
  `tenant_id`     BIGINT       NOT NULL COMMENT '租户ID',
  `course_id`     BIGINT       NOT NULL COMMENT '课程ID(逻辑外键 prf_course.id)',
  `instructor_id` BIGINT       DEFAULT NULL COMMENT '讲师ID(逻辑外键 prf_instructor.id)',
  `start_at`      DATETIME(3)  DEFAULT NULL COMMENT '开课时间(UTC)',
  `capacity`      INT          NOT NULL DEFAULT 0 COMMENT '容量',
  `created_by`    BIGINT       NOT NULL DEFAULT 0 COMMENT '创建人ID,0=系统',
  `updated_by`    BIGINT       NOT NULL DEFAULT 0 COMMENT '更新人ID',
  `created_at`    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间(UTC)',
  `updated_at`    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '更新时间(UTC,应用层维护)',
  `deleted`       TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除 0=否 1=是',
  `version`       INT          NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
  PRIMARY KEY (`id`),
  KEY `idx_prf_class_session` (`tenant_id`, `course_id`, `start_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='开班场次';

-- 报名/参训
CREATE TABLE prf_enrollment (
  `id`            BIGINT       NOT NULL COMMENT '主键: 雪花ID',
  `tenant_id`     BIGINT       NOT NULL COMMENT '租户ID',
  `session_id`    BIGINT       NOT NULL COMMENT '场次ID(逻辑外键 prf_class_session.id)',
  `emp_id`        BIGINT       NOT NULL COMMENT '员工ID(逻辑外键 emp_employee.id)',
  `status`        VARCHAR(16)  NOT NULL DEFAULT 'ENROLLED' COMMENT '状态 ENROLLED/COMPLETED/CANCELLED',
  `created_by`    BIGINT       NOT NULL DEFAULT 0 COMMENT '创建人ID,0=系统',
  `updated_by`    BIGINT       NOT NULL DEFAULT 0 COMMENT '更新人ID',
  `created_at`    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间(UTC)',
  `updated_at`    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '更新时间(UTC,应用层维护)',
  `deleted`       TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除 0=否 1=是',
  `version`       INT          NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_prf_enrollment` (`tenant_id`, `session_id`, `emp_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='报名参训';

-- 学时台账
CREATE TABLE prf_credit_ledger (
  `id`            BIGINT       NOT NULL COMMENT '主键: 雪花ID',
  `tenant_id`     BIGINT       NOT NULL COMMENT '租户ID',
  `emp_id`        BIGINT       NOT NULL COMMENT '员工ID(逻辑外键 emp_employee.id)',
  `course_id`     BIGINT       DEFAULT NULL COMMENT '课程ID(逻辑外键 prf_course.id)',
  `credits`       DECIMAL(6,2) NOT NULL DEFAULT 0 COMMENT '获得学时',
  `created_by`    BIGINT       NOT NULL DEFAULT 0 COMMENT '创建人ID,0=系统',
  `updated_by`    BIGINT       NOT NULL DEFAULT 0 COMMENT '更新人ID',
  `created_at`    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间(UTC)',
  `updated_at`    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '更新时间(UTC,应用层维护)',
  `deleted`       TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除 0=否 1=是',
  `version`       INT          NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
  PRIMARY KEY (`id`),
  KEY `idx_prf_credit_ledger` (`tenant_id`, `emp_id`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='学时台账';

-- =====================================================================
-- 域 8: flow_db 流程中台(仅 flow_ext_* 扩展表; ACT_* 由 Flowable 引擎自动建)
-- =====================================================================
CREATE DATABASE IF NOT EXISTS flow_db DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
USE flow_db;

-- 业务单据-流程实例映射(发起幂等锁)
CREATE TABLE flow_ext_biz_relation (
  `id`            BIGINT       NOT NULL COMMENT '主键: 雪花ID',
  `tenant_id`     BIGINT       NOT NULL COMMENT '租户ID',
  `proc_inst_id`  VARCHAR(64)  NOT NULL COMMENT '流程实例ID(逻辑外键 ACT_HI_PROCINST.ID_)',
  `biz_type`      VARCHAR(32)  NOT NULL COMMENT '业务类型(如 ATT_LEAVE)',
  `biz_key`       VARCHAR(64)  NOT NULL COMMENT '业务单号',
  `status`        VARCHAR(16)  NOT NULL DEFAULT 'RUNNING' COMMENT '状态 RUNNING/FINISHED/TERMINATED',
  `created_by`    BIGINT       NOT NULL DEFAULT 0 COMMENT '创建人ID,0=系统',
  `updated_by`    BIGINT       NOT NULL DEFAULT 0 COMMENT '更新人ID',
  `created_at`    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间(UTC)',
  `updated_at`    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '更新时间(UTC,应用层维护)',
  `deleted`       TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除 0=否 1=是',
  `version`       INT          NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_flow_biz_rel` (`tenant_id`, `biz_type`, `biz_key`),
  KEY `idx_flow_biz_rel_inst` (`tenant_id`, `proc_inst_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='业务单据流程映射';

-- 模型草稿/部署记录
CREATE TABLE flow_ext_model (
  `id`               BIGINT       NOT NULL COMMENT '主键: 雪花ID',
  `tenant_id`        BIGINT       NOT NULL COMMENT '租户ID',
  `proc_def_key`     VARCHAR(64)  NOT NULL COMMENT '流程定义Key',
  `version`          INT          NOT NULL DEFAULT 1 COMMENT '模型版本',
  `bpmn_xml`         MEDIUMTEXT   DEFAULT NULL COMMENT 'BPMN XML(设计态)',
  `form_schema_json` JSON         DEFAULT NULL COMMENT '表单JSON Schema',
  `deploy_state`     VARCHAR(16)  NOT NULL DEFAULT 'DRAFT' COMMENT '状态 DRAFT/DEPLOYED',
  `created_by`       BIGINT       NOT NULL DEFAULT 0 COMMENT '创建人ID,0=系统',
  `updated_by`       BIGINT       NOT NULL DEFAULT 0 COMMENT '更新人ID',
  `created_at`       DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间(UTC)',
  `updated_at`       DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '更新时间(UTC,应用层维护)',
  `deleted`          TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除 0=否 1=是',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_flow_model` (`tenant_id`, `proc_def_key`, `version`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='流程模型';

-- 节点级表单绑定
CREATE TABLE flow_ext_node_form (
  `id`               BIGINT       NOT NULL COMMENT '主键: 雪花ID',
  `tenant_id`        BIGINT       NOT NULL COMMENT '租户ID',
  `proc_def_id`      VARCHAR(64)  NOT NULL COMMENT '流程定义ID',
  `node_id`          VARCHAR(64)  NOT NULL COMMENT '节点ID',
  `form_schema_json` JSON         DEFAULT NULL COMMENT '节点表单JSON Schema',
  `readonly_expr`    VARCHAR(512) DEFAULT NULL COMMENT '只读表达式',
  `created_by`       BIGINT       NOT NULL DEFAULT 0 COMMENT '创建人ID,0=系统',
  `updated_by`       BIGINT       NOT NULL DEFAULT 0 COMMENT '更新人ID',
  `created_at`       DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间(UTC)',
  `updated_at`       DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '更新时间(UTC,应用层维护)',
  `deleted`          TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除 0=否 1=是',
  `version`          INT          NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_flow_node_form` (`tenant_id`, `proc_def_id`, `node_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='节点表单绑定';

-- 审批动作明细
CREATE TABLE flow_ext_task_action (
  `id`            BIGINT       NOT NULL COMMENT '主键: 雪花ID',
  `tenant_id`     BIGINT       NOT NULL COMMENT '租户ID',
  `task_id`       VARCHAR(64)  NOT NULL COMMENT '任务ID(逻辑外键 ACT_RU_TASK.ID_)',
  `action`        VARCHAR(16)  NOT NULL COMMENT '动作 APPROVE/REJECT/TRANSFER/ADD_SIGN/WITHDRAW',
  `operator`      BIGINT       NOT NULL COMMENT '操作人用户ID(逻辑外键 auth_user.id)',
  `comment`       VARCHAR(512) DEFAULT NULL COMMENT '审批意见',
  `attachments`   JSON         DEFAULT NULL COMMENT '附件(json)',
  `delegate_from` BIGINT       DEFAULT NULL COMMENT '委托来源用户ID(加签/转办链)',
  `ts`            DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '动作时间(UTC)',
  `created_by`    BIGINT       NOT NULL DEFAULT 0 COMMENT '创建人ID,0=系统',
  `updated_by`    BIGINT       NOT NULL DEFAULT 0 COMMENT '更新人ID',
  `created_at`    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间(UTC)',
  `updated_at`    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '更新时间(UTC,应用层维护)',
  `deleted`       TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除 0=否 1=是',
  `version`       INT          NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
  PRIMARY KEY (`id`),
  KEY `idx_flow_task_action` (`tenant_id`, `task_id`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='审批动作明细';

-- 事件出站表(事务消息兜底)
CREATE TABLE flow_ext_event_outbox (
  `id`            BIGINT       NOT NULL COMMENT '主键: 雪花ID',
  `event_id`      VARCHAR(64)  NOT NULL COMMENT '事件ID(幂等)',
  `topic`         VARCHAR(128) NOT NULL COMMENT 'MQ Topic',
  `payload`       JSON         NOT NULL COMMENT '事件载荷',
  `state`         VARCHAR(16)  NOT NULL DEFAULT 'PENDING' COMMENT '状态 PENDING/SENT/DEAD',
  `retry_cnt`     INT          NOT NULL DEFAULT 0 COMMENT '重试次数',
  `next_retry_at` DATETIME(3)  DEFAULT NULL COMMENT '下次重试时间(UTC)',
  `created_by`    BIGINT       NOT NULL DEFAULT 0 COMMENT '创建人ID,0=系统',
  `updated_by`    BIGINT       NOT NULL DEFAULT 0 COMMENT '更新人ID',
  `created_at`    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间(UTC)',
  `updated_at`    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '更新时间(UTC,应用层维护)',
  `deleted`       TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除 0=否 1=是',
  `version`       INT          NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_flow_event_id` (`event_id`),
  KEY `idx_flow_outbox_state` (`state`, `next_retry_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='事件出站表';

-- 节点超时催办/升级配置
CREATE TABLE flow_ext_timeout_cfg (
  `id`               BIGINT       NOT NULL COMMENT '主键: 雪花ID',
  `tenant_id`        BIGINT       NOT NULL COMMENT '租户ID',
  `proc_def_id`      VARCHAR(64)  NOT NULL COMMENT '流程定义ID',
  `node_id`          VARCHAR(64)  NOT NULL COMMENT '节点ID',
  `remind_cron`      VARCHAR(64)  DEFAULT NULL COMMENT '催办cron表达式',
  `escalate_after_h` INT          DEFAULT NULL COMMENT '升级小时数',
  `escalate_to`      BIGINT       DEFAULT NULL COMMENT '升级目标用户ID',
  `created_by`       BIGINT       NOT NULL DEFAULT 0 COMMENT '创建人ID,0=系统',
  `updated_by`       BIGINT       NOT NULL DEFAULT 0 COMMENT '更新人ID',
  `created_at`       DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间(UTC)',
  `updated_at`       DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '更新时间(UTC,应用层维护)',
  `deleted`          TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除 0=否 1=是',
  `version`          INT          NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_flow_timeout_cfg` (`tenant_id`, `proc_def_id`, `node_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='节点超时配置';

-- =====================================================================
-- 域 9: rag_db 知识库与问答
-- =====================================================================
CREATE DATABASE IF NOT EXISTS rag_db DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
USE rag_db;

-- 知识空间
CREATE TABLE kb_space (
  `id`            BIGINT       NOT NULL COMMENT '主键: 雪花ID',
  `tenant_id`     BIGINT       NOT NULL COMMENT '租户ID',
  `name`          VARCHAR(128) NOT NULL COMMENT '空间名称',
  `code`          VARCHAR(64)  NOT NULL COMMENT '空间编码',
  `description`   VARCHAR(512) DEFAULT NULL COMMENT '空间描述',
  `created_by`    BIGINT       NOT NULL DEFAULT 0 COMMENT '创建人ID,0=系统',
  `updated_by`    BIGINT       NOT NULL DEFAULT 0 COMMENT '更新人ID',
  `created_at`    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间(UTC)',
  `updated_at`    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '更新时间(UTC,应用层维护)',
  `deleted`       TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除 0=否 1=是',
  `version`       INT          NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_kb_space_code` (`tenant_id`, `code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='知识空间';

-- 空间成员
CREATE TABLE kb_space_member (
  `id`            BIGINT       NOT NULL COMMENT '主键: 雪花ID',
  `tenant_id`     BIGINT       NOT NULL COMMENT '租户ID',
  `space_id`      BIGINT       NOT NULL COMMENT '空间ID(逻辑外键 kb_space.id)',
  `user_id`       BIGINT       NOT NULL COMMENT '用户ID(逻辑外键 auth_user.id)',
  `role`          VARCHAR(16)  NOT NULL DEFAULT 'MEMBER' COMMENT '成员角色 OWNER/EDITOR/VIEWER',
  `created_by`    BIGINT       NOT NULL DEFAULT 0 COMMENT '创建人ID,0=系统',
  `updated_by`    BIGINT       NOT NULL DEFAULT 0 COMMENT '更新人ID',
  `created_at`    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间(UTC)',
  `updated_at`    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '更新时间(UTC,应用层维护)',
  `deleted`       TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除 0=否 1=是',
  `version`       INT          NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_kb_space_member` (`tenant_id`, `space_id`, `user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='空间成员';

-- 权限标签字典
CREATE TABLE kb_acl_tag (
  `id`            BIGINT       NOT NULL COMMENT '主键: 雪花ID',
  `tenant_id`     BIGINT       NOT NULL COMMENT '租户ID',
  `name`          VARCHAR(64)  NOT NULL COMMENT '标签名称',
  `code`          VARCHAR(64)  NOT NULL COMMENT '标签编码(密级/部门/角色)',
  `tag_type`      VARCHAR(16)  NOT NULL COMMENT '标签类型 SECRET_LEVEL/DEPT/ROLE',
  `created_by`    BIGINT       NOT NULL DEFAULT 0 COMMENT '创建人ID,0=系统',
  `updated_by`    BIGINT       NOT NULL DEFAULT 0 COMMENT '更新人ID',
  `created_at`    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间(UTC)',
  `updated_at`    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '更新时间(UTC,应用层维护)',
  `deleted`       TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除 0=否 1=是',
  `version`       INT          NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_kb_acl_tag_code` (`tenant_id`, `code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='权限标签字典';

-- 文档主档
CREATE TABLE kb_document (
  `id`            BIGINT       NOT NULL COMMENT '主键: 雪花ID',
  `tenant_id`     BIGINT       NOT NULL COMMENT '租户ID',
  `space_id`      BIGINT       NOT NULL COMMENT '空间ID(逻辑外键 kb_space.id)',
  `title`         VARCHAR(255) NOT NULL COMMENT '文档标题',
  `title_hash`    VARCHAR(64)  NOT NULL COMMENT '标题哈希(去重)',
  `doc_type`      VARCHAR(16)  NOT NULL DEFAULT 'DOC' COMMENT '类型 DOC/FAQ/TABLE',
  `file_id`       BIGINT       DEFAULT NULL COMMENT '源文件ID(逻辑外键 sys_file_meta.id)',
  `created_by`    BIGINT       NOT NULL DEFAULT 0 COMMENT '创建人ID,0=系统',
  `updated_by`    BIGINT       NOT NULL DEFAULT 0 COMMENT '更新人ID',
  `created_at`    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间(UTC)',
  `updated_at`    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '更新时间(UTC,应用层维护)',
  `deleted`       TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除 0=否 1=是',
  `version`       INT          NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_kb_document` (`tenant_id`, `space_id`, `title_hash`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='知识文档';

-- 文档版本(状态机)
CREATE TABLE kb_doc_version (
  `id`              BIGINT       NOT NULL COMMENT '主键: 雪花ID',
  `tenant_id`       BIGINT       NOT NULL COMMENT '租户ID',
  `document_id`     BIGINT       NOT NULL COMMENT '文档ID(逻辑外键 kb_document.id)',
  `version`         INT          NOT NULL DEFAULT 1 COMMENT '版本号',
  `status`          VARCHAR(16)  NOT NULL DEFAULT 'DRAFT' COMMENT '状态 DRAFT/PENDING/PUBLISHED/EXPIRED/ARCHIVED',
  `effective_from`  DATE         DEFAULT NULL COMMENT '生效开始日',
  `effective_to`    DATE         DEFAULT NULL COMMENT '生效结束日',
  `milvus_collection` VARCHAR(64) DEFAULT NULL COMMENT 'Milvus Collection名',
  `created_by`      BIGINT       NOT NULL DEFAULT 0 COMMENT '创建人ID,0=系统',
  `updated_by`      BIGINT       NOT NULL DEFAULT 0 COMMENT '更新人ID',
  `created_at`      DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间(UTC)',
  `updated_at`      DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '更新时间(UTC,应用层维护)',
  `deleted`         TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除 0=否 1=是',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_kb_doc_version` (`tenant_id`, `document_id`, `version`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='文档版本';

-- 切片
CREATE TABLE kb_chunk (
  `id`             BIGINT       NOT NULL COMMENT '主键: 雪花ID',
  `tenant_id`      BIGINT       NOT NULL COMMENT '租户ID',
  `doc_version_id` BIGINT       NOT NULL COMMENT '文档版本ID(逻辑外键 kb_doc_version.id)',
  `space_id`       BIGINT       NOT NULL COMMENT '空间ID(逻辑外键 kb_space.id)',
  `seq`            INT          NOT NULL COMMENT '切片序号',
  `heading_path`   VARCHAR(512) DEFAULT NULL COMMENT '标题层级路径',
  `text`           MEDIUMTEXT   DEFAULT NULL COMMENT '切片文本(占位; chunk 本体入 Milvus/ES)',
  `token_cnt`      INT          NOT NULL DEFAULT 0 COMMENT 'token数',
  `milvus_id`      VARCHAR(64)  DEFAULT NULL COMMENT 'Milvus向量ID',
  `es_id`          VARCHAR(64)  DEFAULT NULL COMMENT 'ES文档ID',
  `acl_tags`       JSON         DEFAULT NULL COMMENT '权限标签(json)',
  `created_by`     BIGINT       NOT NULL DEFAULT 0 COMMENT '创建人ID,0=系统',
  `updated_by`     BIGINT       NOT NULL DEFAULT 0 COMMENT '更新人ID',
  `created_at`     DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间(UTC)',
  `updated_at`     DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '更新时间(UTC,应用层维护)',
  `deleted`        TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除 0=否 1=是',
  `version`        INT          NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
  PRIMARY KEY (`id`),
  KEY `idx_kb_chunk_version` (`tenant_id`, `doc_version_id`, `seq`),
  KEY `idx_kb_chunk_space` (`tenant_id`, `space_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='知识切片';

-- 问答会话
CREATE TABLE qa_session (
  `id`            BIGINT       NOT NULL COMMENT '主键: 雪花ID',
  `tenant_id`     BIGINT       NOT NULL COMMENT '租户ID',
  `user_id`       BIGINT       NOT NULL COMMENT '用户ID(逻辑外键 auth_user.id)',
  `title`         VARCHAR(128) DEFAULT NULL COMMENT '会话标题',
  `created_by`    BIGINT       NOT NULL DEFAULT 0 COMMENT '创建人ID,0=系统',
  `updated_by`    BIGINT       NOT NULL DEFAULT 0 COMMENT '更新人ID',
  `created_at`    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间(UTC)',
  `updated_at`    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '更新时间(UTC,应用层维护)',
  `deleted`       TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除 0=否 1=是',
  `version`       INT          NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
  PRIMARY KEY (`id`),
  KEY `idx_qa_session_user` (`tenant_id`, `user_id`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='问答会话';

-- 问答消息
CREATE TABLE qa_message (
  `id`            BIGINT       NOT NULL COMMENT '主键: 雪花ID',
  `tenant_id`     BIGINT       NOT NULL COMMENT '租户ID',
  `session_id`    BIGINT       NOT NULL COMMENT '会话ID(逻辑外键 qa_session.id)',
  `role`          VARCHAR(16)  NOT NULL COMMENT '角色 USER/ASSISTANT',
  `question`      TEXT         DEFAULT NULL COMMENT '问题(用户消息)',
  `answer`        TEXT         DEFAULT NULL COMMENT '回答(助手消息)',
  `refs`          JSON         DEFAULT NULL COMMENT '引用列表(json)',
  `tool_calls`    JSON         DEFAULT NULL COMMENT '工具调用(json)',
  `latency_ms`    INT          DEFAULT NULL COMMENT '响应延迟(ms)',
  `model`         VARCHAR(64)  DEFAULT NULL COMMENT '模型标识',
  `created_by`    BIGINT       NOT NULL DEFAULT 0 COMMENT '创建人ID,0=系统',
  `updated_by`    BIGINT       NOT NULL DEFAULT 0 COMMENT '更新人ID',
  `created_at`    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间(UTC)',
  `updated_at`    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '更新时间(UTC,应用层维护)',
  `deleted`       TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除 0=否 1=是',
  `version`       INT          NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
  PRIMARY KEY (`id`),
  KEY `idx_qa_message_session` (`tenant_id`, `session_id`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='问答消息';

-- 问答反馈
CREATE TABLE qa_feedback (
  `id`              BIGINT       NOT NULL COMMENT '主键: 雪花ID',
  `tenant_id`       BIGINT       NOT NULL COMMENT '租户ID',
  `message_id`      BIGINT       NOT NULL COMMENT '消息ID(逻辑外键 qa_message.id)',
  `type`            VARCHAR(16)  NOT NULL COMMENT '反馈类型 LIKE/DISLIKE',
  `correction_text` VARCHAR(512) DEFAULT NULL COMMENT '纠错文本',
  `created_by`      BIGINT       NOT NULL DEFAULT 0 COMMENT '创建人ID,0=系统',
  `updated_by`      BIGINT       NOT NULL DEFAULT 0 COMMENT '更新人ID',
  `created_at`      DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间(UTC)',
  `updated_at`      DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '更新时间(UTC,应用层维护)',
  `deleted`         TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除 0=否 1=是',
  `version`         INT          NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
  PRIMARY KEY (`id`),
  KEY `idx_qa_feedback_msg` (`tenant_id`, `message_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='问答反馈';

-- 知识治理工单
CREATE TABLE kb_govern_ticket (
  `id`             BIGINT       NOT NULL COMMENT '主键: 雪花ID',
  `tenant_id`      BIGINT       NOT NULL COMMENT '租户ID',
  `doc_version_id` BIGINT       DEFAULT NULL COMMENT '文档版本ID(逻辑外键 kb_doc_version.id)',
  `source`         VARCHAR(16)  NOT NULL COMMENT '来源 FEEDBACK/AUDIT',
  `status`         VARCHAR(16)  NOT NULL DEFAULT 'OPEN' COMMENT '状态 OPEN/PROCESSING/CLOSED',
  `description`    VARCHAR(512) DEFAULT NULL COMMENT '工单描述',
  `created_by`     BIGINT       NOT NULL DEFAULT 0 COMMENT '创建人ID,0=系统',
  `updated_by`     BIGINT       NOT NULL DEFAULT 0 COMMENT '更新人ID',
  `created_at`     DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间(UTC)',
  `updated_at`     DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '更新时间(UTC,应用层维护)',
  `deleted`        TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除 0=否 1=是',
  `version`        INT          NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
  PRIMARY KEY (`id`),
  KEY `idx_kb_govern_ticket` (`tenant_id`, `doc_version_id`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='知识治理工单';

-- 评测集
CREATE TABLE kb_eval_set (
  `id`            BIGINT       NOT NULL COMMENT '主键: 雪花ID',
  `tenant_id`     BIGINT       NOT NULL COMMENT '租户ID',
  `name`          VARCHAR(128) NOT NULL COMMENT '评测集名称',
  `code`          VARCHAR(64)  NOT NULL COMMENT '评测集编码',
  `created_by`    BIGINT       NOT NULL DEFAULT 0 COMMENT '创建人ID,0=系统',
  `updated_by`    BIGINT       NOT NULL DEFAULT 0 COMMENT '更新人ID',
  `created_at`    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间(UTC)',
  `updated_at`    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '更新时间(UTC,应用层维护)',
  `deleted`       TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除 0=否 1=是',
  `version`       INT          NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_kb_eval_set_code` (`tenant_id`, `code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='评测集';

-- 评测用例
CREATE TABLE kb_eval_case (
  `id`            BIGINT       NOT NULL COMMENT '主键: 雪花ID',
  `tenant_id`     BIGINT       NOT NULL COMMENT '租户ID',
  `eval_set_id`   BIGINT       NOT NULL COMMENT '评测集ID(逻辑外键 kb_eval_set.id)',
  `question`      VARCHAR(512) NOT NULL COMMENT '问题',
  `expected_refs` JSON         DEFAULT NULL COMMENT '期望引用(json)',
  `ground_truth`  VARCHAR(512) DEFAULT NULL COMMENT '标准答案',
  `created_by`    BIGINT       NOT NULL DEFAULT 0 COMMENT '创建人ID,0=系统',
  `updated_by`    BIGINT       NOT NULL DEFAULT 0 COMMENT '更新人ID',
  `created_at`    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间(UTC)',
  `updated_at`    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '更新时间(UTC,应用层维护)',
  `deleted`       TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除 0=否 1=是',
  `version`       INT          NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
  PRIMARY KEY (`id`),
  KEY `idx_kb_eval_case_set` (`tenant_id`, `eval_set_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='评测用例';

-- 评测运行
CREATE TABLE kb_eval_run (
  `id`            BIGINT       NOT NULL COMMENT '主键: 雪花ID',
  `tenant_id`     BIGINT       NOT NULL COMMENT '租户ID',
  `eval_set_id`   BIGINT       NOT NULL COMMENT '评测集ID(逻辑外键 kb_eval_set.id)',
  `metrics`       JSON         DEFAULT NULL COMMENT '指标快照(json)',
  `status`        VARCHAR(16)  NOT NULL DEFAULT 'RUNNING' COMMENT '状态 RUNNING/DONE/FAILED',
  `created_by`    BIGINT       NOT NULL DEFAULT 0 COMMENT '创建人ID,0=系统',
  `updated_by`    BIGINT       NOT NULL DEFAULT 0 COMMENT '更新人ID',
  `created_at`    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间(UTC)',
  `updated_at`    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '更新时间(UTC,应用层维护)',
  `deleted`       TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除 0=否 1=是',
  `version`       INT          NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
  PRIMARY KEY (`id`),
  KEY `idx_kb_eval_run_set` (`tenant_id`, `eval_set_id`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='评测运行';

-- =====================================================================
-- 域 10: plt_db 平台支撑
-- =====================================================================
CREATE DATABASE IF NOT EXISTS plt_db DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
USE plt_db;

-- 字典类型(广播表)
CREATE TABLE sys_dict_type (
  `id`            BIGINT       NOT NULL COMMENT '主键: 雪花ID',
  `tenant_id`     BIGINT       NOT NULL COMMENT '租户ID',
  `name`          VARCHAR(64)  NOT NULL COMMENT '字典类型名称',
  `code`          VARCHAR(64)  NOT NULL COMMENT '字典类型编码',
  `created_by`    BIGINT       NOT NULL DEFAULT 0 COMMENT '创建人ID,0=系统',
  `updated_by`    BIGINT       NOT NULL DEFAULT 0 COMMENT '更新人ID',
  `created_at`    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间(UTC)',
  `updated_at`    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '更新时间(UTC,应用层维护)',
  `deleted`       TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除 0=否 1=是',
  `version`       INT          NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sys_dict_type_code` (`tenant_id`, `code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='字典类型';

-- 字典项(广播表)
CREATE TABLE sys_dict_item (
  `id`            BIGINT       NOT NULL COMMENT '主键: 雪花ID',
  `tenant_id`     BIGINT       NOT NULL COMMENT '租户ID',
  `type_code`     VARCHAR(64)  NOT NULL COMMENT '字典类型编码(逻辑外键 sys_dict_type.code)',
  `label`         VARCHAR(64)  NOT NULL COMMENT '字典项标签',
  `value`         VARCHAR(64)  NOT NULL COMMENT '字典项值',
  `sort`          INT          NOT NULL DEFAULT 0 COMMENT '排序',
  `created_by`    BIGINT       NOT NULL DEFAULT 0 COMMENT '创建人ID,0=系统',
  `updated_by`    BIGINT       NOT NULL DEFAULT 0 COMMENT '更新人ID',
  `created_at`    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间(UTC)',
  `updated_at`    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '更新时间(UTC,应用层维护)',
  `deleted`       TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除 0=否 1=是',
  `version`       INT          NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sys_dict_item` (`tenant_id`, `type_code`, `value`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='字典项';

-- 租户参数(广播表)
CREATE TABLE sys_tenant_config (
  `id`            BIGINT       NOT NULL COMMENT '主键: 雪花ID',
  `tenant_id`     BIGINT       NOT NULL COMMENT '租户ID',
  `config_key`    VARCHAR(64)  NOT NULL COMMENT '配置键',
  `config_value`  VARCHAR(512) DEFAULT NULL COMMENT '配置值',
  `created_by`    BIGINT       NOT NULL DEFAULT 0 COMMENT '创建人ID,0=系统',
  `updated_by`    BIGINT       NOT NULL DEFAULT 0 COMMENT '更新人ID',
  `created_at`    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间(UTC)',
  `updated_at`    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '更新时间(UTC,应用层维护)',
  `deleted`       TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除 0=否 1=是',
  `version`       INT          NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sys_tenant_config` (`tenant_id`, `config_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='租户参数';

-- 文件元数据
CREATE TABLE sys_file_meta (
  `id`               BIGINT       NOT NULL COMMENT '主键: 雪花ID',
  `tenant_id`        BIGINT       NOT NULL COMMENT '租户ID',
  `bucket`           VARCHAR(64)  NOT NULL COMMENT 'MinIO桶',
  `object_key`       VARCHAR(255) NOT NULL COMMENT '对象键',
  `file_name`        VARCHAR(255) NOT NULL COMMENT '原始文件名',
  `content_type`     VARCHAR(128) DEFAULT NULL COMMENT '内容类型',
  `size_bytes`       BIGINT       NOT NULL DEFAULT 0 COMMENT '文件大小(字节)',
  `watermark_policy` VARCHAR(16)  DEFAULT NULL COMMENT '水印策略',
  `created_by`       BIGINT       NOT NULL DEFAULT 0 COMMENT '创建人ID,0=系统',
  `updated_by`       BIGINT       NOT NULL DEFAULT 0 COMMENT '更新人ID',
  `created_at`       DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间(UTC)',
  `updated_at`       DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '更新时间(UTC,应用层维护)',
  `deleted`          TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除 0=否 1=是',
  `version`          INT          NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sys_file_meta` (`tenant_id`, `bucket`, `object_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='文件元数据';

-- 通知模板
CREATE TABLE sys_notify_template (
  `id`            BIGINT       NOT NULL COMMENT '主键: 雪花ID',
  `tenant_id`     BIGINT       NOT NULL COMMENT '租户ID',
  `code`          VARCHAR(64)  NOT NULL COMMENT '模板编码',
  `channel`       VARCHAR(16)  NOT NULL COMMENT '渠道 EMAIL/SMS/APP/WECOM',
  `subject`       VARCHAR(128) DEFAULT NULL COMMENT '标题',
  `content`       TEXT         DEFAULT NULL COMMENT '模板内容',
  `created_by`    BIGINT       NOT NULL DEFAULT 0 COMMENT '创建人ID,0=系统',
  `updated_by`    BIGINT       NOT NULL DEFAULT 0 COMMENT '更新人ID',
  `created_at`    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间(UTC)',
  `updated_at`    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '更新时间(UTC,应用层维护)',
  `deleted`       TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除 0=否 1=是',
  `version`       INT          NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sys_notify_template` (`tenant_id`, `code`, `channel`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='通知模板';

-- 通知记录
CREATE TABLE sys_notify_record (
  `id`            BIGINT       NOT NULL COMMENT '主键: 雪花ID',
  `tenant_id`     BIGINT       NOT NULL COMMENT '租户ID',
  `user_id`       BIGINT       NOT NULL COMMENT '接收人用户ID(逻辑外键 auth_user.id)',
  `template_id`   BIGINT       DEFAULT NULL COMMENT '模板ID(逻辑外键 sys_notify_template.id)',
  `channel`       VARCHAR(16)  NOT NULL COMMENT '渠道',
  `send_status`   VARCHAR(16)  NOT NULL DEFAULT 'PENDING' COMMENT '状态 PENDING/SENT/FAILED',
  `sent_at`       DATETIME(3)  DEFAULT NULL COMMENT '发送时间(UTC)',
  `created_by`    BIGINT       NOT NULL DEFAULT 0 COMMENT '创建人ID,0=系统',
  `updated_by`    BIGINT       NOT NULL DEFAULT 0 COMMENT '更新人ID',
  `created_at`    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间(UTC)',
  `updated_at`    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '更新时间(UTC,应用层维护)',
  `deleted`       TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除 0=否 1=是',
  `version`       INT          NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
  PRIMARY KEY (`id`),
  KEY `idx_sys_notify_record` (`tenant_id`, `user_id`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='通知记录';

-- 报表定义
CREATE TABLE sys_report_def (
  `id`            BIGINT       NOT NULL COMMENT '主键: 雪花ID',
  `tenant_id`     BIGINT       NOT NULL COMMENT '租户ID',
  `name`          VARCHAR(128) NOT NULL COMMENT '报表名称',
  `code`          VARCHAR(64)  NOT NULL COMMENT '报表编码',
  `def_json`      JSON         DEFAULT NULL COMMENT '报表定义(json)',
  `created_by`    BIGINT       NOT NULL DEFAULT 0 COMMENT '创建人ID,0=系统',
  `updated_by`    BIGINT       NOT NULL DEFAULT 0 COMMENT '更新人ID',
  `created_at`    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间(UTC)',
  `updated_at`    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '更新时间(UTC,应用层维护)',
  `deleted`       TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除 0=否 1=是',
  `version`       INT          NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sys_report_def_code` (`tenant_id`, `code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='报表定义';

-- 异步导出任务
CREATE TABLE sys_export_task (
  `id`            BIGINT       NOT NULL COMMENT '主键: 雪花ID',
  `tenant_id`     BIGINT       NOT NULL COMMENT '租户ID',
  `report_def_id` BIGINT       DEFAULT NULL COMMENT '报表定义ID(逻辑外键 sys_report_def.id)',
  `status`        VARCHAR(16)  NOT NULL DEFAULT 'PENDING' COMMENT '状态 PENDING/RUNNING/DONE/FAILED',
  `file_id`       BIGINT       DEFAULT NULL COMMENT '导出文件ID(逻辑外键 sys_file_meta.id)',
  `created_by`    BIGINT       NOT NULL DEFAULT 0 COMMENT '创建人ID,0=系统',
  `updated_by`    BIGINT       NOT NULL DEFAULT 0 COMMENT '更新人ID',
  `created_at`    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间(UTC)',
  `updated_at`    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '更新时间(UTC,应用层维护)',
  `deleted`       TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除 0=否 1=是',
  `version`       INT          NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
  PRIMARY KEY (`id`),
  KEY `idx_sys_export_task` (`tenant_id`, `status`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='异步导出任务';

-- 审计日志(只追加, 月分区; 分区由调度任务维护)
CREATE TABLE sys_audit_log (
  `id`            BIGINT       NOT NULL COMMENT '主键: 雪花ID',
  `tenant_id`     BIGINT       NOT NULL COMMENT '租户ID',
  `actor_id`      BIGINT       DEFAULT NULL COMMENT '操作人用户ID',
  `action`        VARCHAR(64)  NOT NULL COMMENT '审计动作',
  `resource`      VARCHAR(128) DEFAULT NULL COMMENT '资源标识',
  `detail`        JSON         DEFAULT NULL COMMENT '审计明细(json)',
  `ip`            VARCHAR(64)  DEFAULT NULL COMMENT '来源IP',
  `created_at`    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间(UTC,分区键)',
  PRIMARY KEY (`id`, `created_at`),
  KEY `idx_sys_audit_actor` (`tenant_id`, `actor_id`),
  KEY `idx_sys_audit_time` (`tenant_id`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='审计日志(只追加)'
PARTITION BY RANGE COLUMNS(`created_at`) (
  PARTITION p202601 VALUES LESS THAN ('2026-02-01'),
  PARTITION p202602 VALUES LESS THAN ('2026-03-01'),
  PARTITION p202603 VALUES LESS THAN ('2026-04-01'),
  PARTITION pmax VALUES LESS THAN (MAXVALUE)
);

-- 调度任务注册
CREATE TABLE sys_scheduler_task_registry (
  `id`            BIGINT       NOT NULL COMMENT '主键: 雪花ID',
  `tenant_id`     BIGINT       NOT NULL COMMENT '租户ID',
  `task_key`      VARCHAR(64)  NOT NULL COMMENT '任务键',
  `cron`          VARCHAR(64)  NOT NULL COMMENT 'cron表达式',
  `handler`       VARCHAR(128) NOT NULL COMMENT '处理器标识',
  `status`        TINYINT      NOT NULL DEFAULT 1 COMMENT '状态 1=启用 0=停用',
  `created_by`    BIGINT       NOT NULL DEFAULT 0 COMMENT '创建人ID,0=系统',
  `updated_by`    BIGINT       NOT NULL DEFAULT 0 COMMENT '更新人ID',
  `created_at`    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间(UTC)',
  `updated_at`    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '更新时间(UTC,应用层维护)',
  `deleted`       TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除 0=否 1=是',
  `version`       INT          NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sys_scheduler_task` (`tenant_id`, `task_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='调度任务注册';

CREATE TABLE sys_scheduler_task_log (
  `id`            BIGINT       NOT NULL COMMENT '主键: 雪花ID',
  `tenant_id`     BIGINT       NOT NULL COMMENT '租户ID',
  `task_id`       BIGINT       DEFAULT NULL COMMENT '注册任务ID',
  `task_key`      VARCHAR(64)  NOT NULL COMMENT '任务键(冗余, 注册表删除后仍可追溯)',
  `handler`       VARCHAR(128) NOT NULL COMMENT '处理器标识',
  `trigger_type`  VARCHAR(16)  NOT NULL COMMENT '触发方式: CRON/MANUAL/RETRY',
  `status`        VARCHAR(16)  NOT NULL COMMENT '结果: SUCCESS/FAILED',
  `retry_no`      INT          NOT NULL DEFAULT 0 COMMENT '第几次重试(0=首次执行)',
  `duration_ms`   BIGINT       DEFAULT NULL COMMENT '耗时(毫秒)',
  `message`       VARCHAR(512) DEFAULT NULL COMMENT '执行摘要或失败原因',
  `created_by`    BIGINT       NOT NULL DEFAULT 0 COMMENT '创建人ID,0=系统',
  `updated_by`    BIGINT       NOT NULL DEFAULT 0 COMMENT '更新人ID',
  `created_at`    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '执行时间(UTC)',
  `updated_at`    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '更新时间(UTC,应用层维护)',
  `deleted`       TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除 0=否 1=是',
  `version`       INT          NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
  PRIMARY KEY (`id`),
  KEY `idx_sys_scheduler_log` (`tenant_id`, `task_key`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='调度任务执行日志';

-- 全局事件表(事务消息模板)
CREATE TABLE sys_outbox_message (
  `id`            BIGINT       NOT NULL COMMENT '主键: 雪花ID',
  `event_id`      VARCHAR(64)  NOT NULL COMMENT '事件ID(幂等)',
  `topic`         VARCHAR(128) NOT NULL COMMENT 'MQ Topic',
  `payload`       JSON         NOT NULL COMMENT '事件载荷',
  `state`         VARCHAR(16)  NOT NULL DEFAULT 'PENDING' COMMENT '状态 PENDING/SENT/DEAD',
  `retry_cnt`     INT          NOT NULL DEFAULT 0 COMMENT '重试次数',
  `next_retry_at` DATETIME(3)  DEFAULT NULL COMMENT '下次重试时间(UTC)',
  `created_at`    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间(UTC)',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sys_outbox_event` (`event_id`),
  KEY `idx_sys_outbox_state` (`state`, `next_retry_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='全局事件表';

-- 文件结束: schema-mysql.sql (共 114 张逻辑表, 不含 Flowable ACT_* 引擎表)
