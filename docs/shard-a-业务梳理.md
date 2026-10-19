# Shard-A 业务梳理：hris-common / hris-gateway / hris-auth

> 智云 HR 多租户 SaaS 系统 · 基础设施层（公共库 / 网关 / 认证授权）
> 梳理范围：`src/main/java/**/*.java`，共 70 个主代码文件（common 18 + gateway 17 + auth 35）。

---

## 1. 模块总览

| 模块 | 定位（一句话） | 业务域 | 分层位置 |
| --- | --- | --- | --- |
| **hris-common** | 全系统横切公共库：统一响应体 R、错误码枚举、业务异常、全局异常处理、MyBatis-Plus 租户插件、权限切面、链路追踪、审计切面，被所有业务模块依赖。 | common（公共库） | SDK / Starter |
| **hris-gateway** | 基于 Spring Cloud Gateway（WebFlux 响应式）的统一入口：路由转发、JWT 验签、白名单放行、内部端点封禁、请求体守卫、Sentinel 限流、traceId 透传、统一错误响应。 | gateway（网关） | Edge / Gateway |
| **hris-auth** | 认证授权服务：账号密码登录、JWT access/refresh 签发与刷新、登出撤销、当前用户/菜单/权限点查询、MFA（TOTP）绑定校验、内部入职建号/离职禁用。是多租户 SaaS 的身份提供者（IdP）。 | auth（认证授权） | Service |

---

## 2. 包结构

### 2.1 hris-common（com.hris.common）

| 全限定类名 | 分层位置 | 一句话职责 |
| --- | --- | --- |
| `com.hris.common.api.R` | DTO / 响应包装 | 统一响应体 `{code, message, data, traceId, timestamp}`，所有模块 Controller 返回值统一用它包装。 |
| `com.hris.common.dto.PageResult` | DTO | 分页结果 record `{records[], page, size, total}`，各业务模块复用。 |
| `com.hris.common.entity.BaseEntity` | Entity 基类 | 租户业务表公共字段基类：id（雪花）、tenantId、createdBy/updatedBy、createdAt/updatedAt、deleted（逻辑删除）、version（乐观锁）。 |
| `com.hris.common.exception.BizException` | Exception | 业务异常，携带 code，由 GlobalExceptionHandler 转 R.fail。 |
| `com.hris.common.exception.ErrorCode` | Constant 枚举 | 错误码分段：0 成功；1xxxx 通用；2xxxx 认证授权；3xxxx 流程；4xxxx RAG；5xxxx 业务域；50000+ 系统内部。 |
| `com.hris.common.exception.GlobalExceptionHandler` | Handler | Servlet 侧全局异常兜底（BizException/参数校验/未捕获异常），仅 SERVLET 应用加载。 |
| `com.hris.common.exception.NotFoundExceptionHandler` | Handler | 把 NoResourceFoundException（路径不存在）还原为 404，避免被兜底吞成 500。 |
| `com.hris.common.exception.ValidationExceptionHandler` | Handler | Jakarta Validation ConstraintViolationException 处理器，条件加载。 |
| `com.hris.common.audit.AuditLog` | Annotation | 审计埋点注解（标记登录/权限变更/敏感操作等关键方法）。 |
| `com.hris.common.audit.AuditLogAspect` | Aspect | 审计切面：环绕 @AuditLog 方法，输出结构化审计日志（traceId/tenantId/userId/module/op/ok/costMs）。 |
| `com.hris.common.mybatis.MybatisPlusConfig` | Config | MyBatis-Plus 装配：租户隔离插件（TenantLineInnerInterceptor）+ 乐观锁 + 分页 + 公共字段自动填充。 |
| `com.hris.common.security.AuthContextFilter` | Filter | Servlet 侧从网关透传头还原 AuthContextHolder（userId/tenantId/perms/roles）。 |
| `com.hris.common.security.AuthContextHolder` | Context（ThreadLocal） | 下游服务权限上下文 ThreadLocal，供 PermAspect 做垂直越权拦截。 |
| `com.hris.common.security.JwtSecretGuard` | Util | JWT HS256 密钥启动期强校验：拒绝空值/弱密钥/已知默认值，防止自签令牌伪造。 |
| `com.hris.common.security.PermAspect` | Aspect | 权限校验切面：拦截 @RequirePerm，校验当前用户 perms 是否包含所需权限点，缺失抛 20403。 |
| `com.hris.common.security.RequirePerm` | Annotation | 权限点校验注解（模块:资源:动作），标注受保护端点。 |
| `com.hris.common.trace.TraceContext` | Context（ThreadLocal） | 链路追踪上下文：traceId / tenantId / userId，贯穿请求→DB→MQ。 |
| `com.hris.common.trace.TraceIdFilter` | Filter | Servlet 侧 traceId 透传过滤器：从 X-Trace-Id 取或生成 UUID，解析 X-Tenant-Id/X-User-Id 写入 TraceContext。 |

### 2.2 hris-gateway（com.hris.gateway）

| 全限定类名 | 分层位置 | 一句话职责 |
| --- | --- | --- |
| `com.hris.gateway.GatewayApplication` | 启动类 | WebFlux 网关服务入口，scanBasePackages=com.hris 扫描 common 公共组件。 |
| `com.hris.gateway.common.GatewayConstants` | Constant | 网关过滤链共享常量：请求/响应头、交换属性键、JWT claims 字段名（与 auth TokenService 对齐）。 |
| `com.hris.gateway.config.CorsConfig` | Config | 全局 CORS（WebFlux）：从配置读允许来源/方法/头，生产禁止 origin:*。 |
| `com.hris.gateway.config.HrisGatewayProperties` | ConfigProperties | 网关运行参数：白名单、内部端点标识、CORS、请求体大小、Sentinel 限流规则。 |
| `com.hris.gateway.config.JwtProperties` | ConfigProperties | 网关 JWT 验签配置（HS256 密钥 + issuer，与 auth 对齐）。 |
| `com.hris.gateway.config.SentinelConfig` | Config | Sentinel 网关限流装配：端点维度 QPS FlowRule + 客户端 IP 解析器。 |
| `com.hris.gateway.filter.AccessLogGlobalFilter` | GlobalFilter | 请求访问日志（ORDER=-900，最外层业务过滤器），被拒绝的请求也记录。 |
| `com.hris.gateway.filter.InternalBanWebFilter` | WebFilter | 内部端点封禁（/internal/ 路径公网访问一律 403），WebFilter 层先于路由匹配。 |
| `com.hris.gateway.filter.JwtAuthGlobalFilter` | GlobalFilter | JWT 认证（ORDER=-600）：验签后透传 X-User-Id/X-Tenant-Id/X-User-Roles/X-User-Perms，丢弃客户端伪造头。 |
| `com.hris.gateway.filter.RequestSizeGuardGlobalFilter` | GlobalFilter | 请求体大小守卫（ORDER=-850）：Content-Length 超限 413。 |
| `com.hris.gateway.filter.TraceIdGlobalFilter` | GlobalFilter | 入口 traceId 过滤器（ORDER=-1000，最外层）：生成/透传 X-Trace-Id。 |
| `com.hris.gateway.filter.WhitelistGlobalFilter` | GlobalFilter | 白名单过滤器（ORDER=-800）：命中白名单则标记跳过 JWT 校验。 |
| `com.hris.gateway.handler.GatewayErrorWebExceptionHandler` | Handler | WebFlux 全局异常处理：404/503/504/500 收敛为统一 R 响应体。 |
| `com.hris.gateway.handler.GatewayErrorWriter` | Handler / Util | 网关统一错误响应写出器：组装 R JSON 并写入 WebFlux 响应。 |
| `com.hris.gateway.handler.SentinelBlockRequestHandler` | Handler | Sentinel 限流触发时的响应处理（429 + R body，code=10429）。 |
| `com.hris.gateway.jwt.GatewayPrincipal` | DTO（record） | 网关解析 JWT 后的主体信息：userId/tenantId/empId/roles/perms。 |
| `com.hris.gateway.jwt.JwtTokenVerifier` | Component | 网关 JWT 验签器（HS256）：签名/issuer/类型校验，输出 GatewayPrincipal。 |

### 2.3 hris-auth（com.hris.auth）

| 全限定类名 | 分层位置 | 一句话职责 |
| --- | --- | --- |
| `com.hris.auth.AuthApplication` | 启动类 | 认证授权服务入口，scanBasePackages=com.hris 扫描 common 公共组件。 |
| `com.hris.auth.config.AuthContext` | Context（ThreadLocal） | 认证上下文：当前 LoginUser，由 JwtAuthenticationFilter 写入。 |
| `com.hris.auth.config.AuthProperties` | ConfigProperties | auth 服务配置：JWT 密钥、issuer、access TTL（15min）、refresh TTL（7d）。 |
| `com.hris.auth.config.JwtAuthenticationFilter` | Filter | Servlet 侧 JWT 过滤器：解析 Bearer 令牌写入 TraceContext 与 AuthContext。 |
| `com.hris.auth.config.SecurityBeansConfig` | Config | 安全 Bean 装配：Argon2id 密码哈希编码器。 |
| `com.hris.auth.controller.AuthController` | Controller | 认证端点：登录/刷新/登出/me/menus/mfa-bind/mfa-verify。 |
| `com.hris.auth.controller.AccountInternalController` | Controller | 内部端点：入职建号/离职禁用（仅服务间 Feign，网关封禁公网）。 |
| `com.hris.auth.dto.LoginRequest` | DTO（record） | 登录请求：tenantCode + username + password。 |
| `com.hris.auth.dto.RefreshRequest` | DTO（record） | 刷新令牌请求：refreshToken。 |
| `com.hris.auth.dto.LogoutRequest` | DTO（record） | 登出请求：可选 refreshToken。 |
| `com.hris.auth.dto.TokenResponse` | DTO（record） | 令牌响应：accessToken/refreshToken/tokenType/expiresIn/refreshExpiresIn。 |
| `com.hris.auth.dto.MeResponse` | DTO（record） | 当前用户摘要：user/roles/perms/dataScopes/tenant。 |
| `com.hris.auth.dto.MenuNode` | DTO（record） | 菜单树节点：code/name/type/children。 |
| `com.hris.auth.dto.MfaDtos` | DTO（record 集合） | MFA 绑定响应（secret+otpauthUrl）与校验请求（code）。 |
| `com.hris.auth.dto.AccountDtos` | DTO（record 集合） | 内部建号请求/响应 DTO。 |
| `com.hris.auth.entity.AuthTenant` | Entity | 租户主档（平台级表，不带 tenant_id）：code/name/storageMode/plan/status/timezone/domain。 |
| `com.hris.auth.entity.AuthUser` | Entity | 用户账号（继承 BaseEntity）：employeeId/username/pwdHash/mfaSecret/status。 |
| `com.hris.auth.entity.AuthRole` | Entity | 租户角色（RBAC）：code/name/builtin。 |
| `com.hris.auth.entity.AuthUserRole` | Entity | 用户-角色关联：userId/roleId。 |
| `com.hris.auth.entity.AuthRolePerm` | Entity | 角色-权限点关联：roleId/permCode（模块:资源:动作）。 |
| `com.hris.auth.entity.AuthDataScope` | Entity | 数据权限规则：roleId/scopeType(SELF/DEPT/DEPT_SUB/CUSTOM/ALL)/deptIds。 |
| `com.hris.auth.mapper.AuthTenantMapper` | Mapper | 租户主档 DAO：findByCode/findByDomain/findByIdIgnoreTenant（@InterceptorIgnore 绕过租户插件）。 |
| `com.hris.auth.mapper.AuthUserMapper` | Mapper | 用户账号 DAO。 |
| `com.hris.auth.mapper.AuthUserRoleMapper` | Mapper | 用户-角色 DAO：selectRoleIdsByUserId。 |
| `com.hris.auth.mapper.AuthRoleMapper` | Mapper | 角色 DAO。 |
| `com.hris.auth.mapper.AuthRolePermMapper` | Mapper | 角色-权限点 DAO：selectPermCodesByRoleIds（去重）。 |
| `com.hris.auth.mapper.AuthDataScopeMapper` | Mapper | 数据权限规则 DAO。 |
| `com.hris.auth.model.LoginUser` | Model（record） | 已认证用户上下文：userId/tenantId/employeeId/username/status/roles/perms。 |
| `com.hris.auth.service.AuthService` | Service | 认证业务编排：login/refresh/logout/me/menus。 |
| `com.hris.auth.service.AccountService` | Service | 账号生命周期：内部入职建号/离职禁用。 |
| `com.hris.auth.service.TokenService` | Service | JWT 签发/解析/撤销：access/refresh 令牌管理 + 进程内黑名单与 refreshStore。 |
| `com.hris.auth.service.UserAuthService` | Service | 用户授权信息读取：角色/权限点/数据权限（纯读取）。 |
| `com.hris.auth.service.MenuService` | Service | 菜单树派生：由权限点 模块:资源:动作 三级切分生成。 |
| `com.hris.auth.service.MfaService` | Service | MFA（TOTP）绑定与校验。 |
| `com.hris.auth.util.TotpUtil` | Util | TOTP（RFC 6238）纯函数：Base32 密钥生成、动态码校验（±1 步容差）、otpauth URI。 |

---

## 3. 核心业务流程

### 3.1 网关路由与 JWT 验签流程

```
公网请求
  │
  ▼
TraceIdGlobalFilter (ORDER=-1000)
  │  从 X-Trace-Id 取/生成 UUID → 写入交换属性 + 响应头 + Reactor Context
  ▼
InternalBanWebFilter (WebFilter, ORDER=-700)
  │  路径含 /internal/ → 403 拒绝（内部端点不暴露公网）
  ▼
WhitelistGlobalFilter (ORDER=-800)
  │  AntPathMatcher 匹配白名单 → 命中则写 ATTR_SKIP_AUTH=true
  ▼
AccessLogGlobalFilter (ORDER=-900, doFinally 钩子)
  │  放行后在 doFinally 打印访问日志（method/path/status/traceId/tenantId/userId/costMs）
  ▼
RequestSizeGuardGlobalFilter (ORDER=-850)
  │  Content-Length > maxBodySizeBytes → 413
  ▼
JwtAuthGlobalFilter (ORDER=-600)
  │  ① ATTR_SKIP_AUTH=true → 直接放行（登录/刷新/健康检查）
  │  ② 无 Authorization: Bearer → 401 UNAUTHORIZED
  │  ③ JwtTokenVerifier.verifyAccess(token) 验签（HS256 + issuer + typ=access）
  │     失败 → 401
  │  ④ 丢弃客户端伪造的 X-Tenant-Id/X-User-Id/X-User-Roles/X-User-Perms 头
  │  ⑤ 以 token claims 为准重写上述 4 个头 → 透传下游
  │  ⑥ 主体写入交换属性 + Reactor Context
  ▼
路由转发到下游服务（hris-auth / hris-emp / hris-org ...）
  │
  ▼
下游 Servlet 服务：
  TraceIdFilter（common）→ 还原 traceId/tenantId/userId 到 TraceContext
  AuthContextFilter（common）→ 还原 perms/roles 到 AuthContextHolder
  PermAspect 拦截 @RequirePerm 方法 → 校验权限点
  MybatisPlusConfig.TenantLineInnerInterceptor → SQL 自动追加 AND tenant_id = ?
```

**白名单**：由 `hris.gateway.whitelist` 配置（AntPathMatcher 语义），通常包含 `/api/v1/auth/login`、`/api/v1/auth/refresh`、`/actuator/health` 等公开端点。

**租户上下文注入**：网关验签后从 JWT claims 取 `tenant_id`，重写为 `X-Tenant-Id` 头透传下游；下游 TraceIdFilter 读该头写入 TraceContext；MyBatis-Plus 租户插件从 TraceContext 取 tenantId 追加到 SQL 条件。

### 3.2 登录 / 令牌签发 / 刷新 / 撤销 完整链路

**登录（POST /api/v1/auth/login）**：
1. 网关白名单放行（无 JWT）。
2. AuthController.login → AuthService.login：
   - 按 `tenantCode` 查 `AuthTenant`（`@InterceptorIgnore` 绕过租户插件），校验存在且 status=1。
   - 设置 `TraceContext.tenantId`。
   - 按 `username` 查 `AuthUser`，校验存在且 status=1。
   - Argon2id 校验密码（不区分"用户不存在"与"密码错误"防枚举）。
   - UserAuthService 加载角色编码 + 权限点 → 构建 LoginUser。
   - TokenService.issueAccess 签发 access（15min，claims 含 sub/tenant_id/emp_id/username/status/roles/perms/typ=access）。
   - TokenService.issueRefresh 签发 refresh（7d，jti 写入进程内 refreshStore）。
3. 返回 TokenResponse（accessToken + refreshToken + expiresIn）。

**刷新（POST /api/v1/auth/refresh）**：
1. 网关白名单放行。
2. AuthService.refresh：
   - TokenService.parseRefresh 校验 refresh 签名/类型/jti 存在于 refreshStore/未在黑名单。
   - 查用户状态（禁用则拒绝）。
   - `consumeRefresh(jti)` 移除旧 jti（轮换 + 重用检测：旧 jti 再次使用会被拒）。
   - 重建 LoginUser，签发新 access + 新 refresh。

**登出（POST /api/v1/auth/logout）**：
1. 需登录（Authorization 头带 access）。
2. AuthService.logout → TokenService.revoke：
   - access jti 加入黑名单（TTL 内即时失效，进程内 ConcurrentHashMap）。
   - refresh jti 从 refreshStore 移除并加入黑名单。

**注意**：MVP 阶段黑名单与 refreshStore 均为进程内内存实现，单实例可运行；多实例部署需替换为 Redis（键前缀 `t{tenantId}:`）。

### 3.3 common 包提供的横切能力

| 能力 | 实现类 | 业务作用 |
| --- | --- | --- |
| 统一响应体 | `R<T>` | 所有 Controller 返回 `R.ok(data)` / `R.fail(code, msg)`，前端按 code 判断业务成败。 |
| 错误码分段 | `ErrorCode` 枚举 | 统一错误码语义：20401 未认证、20403 无权限、20501 租户失效、54001 薪酬密域无权限等。 |
| 业务异常 | `BizException` | 业务校验失败时抛出，GlobalExceptionHandler 自动转 R.fail。 |
| 全局异常处理 | `GlobalExceptionHandler` / `NotFoundExceptionHandler` / `ValidationExceptionHandler` | Servlet 侧异常兜底，系统错误不泄露堆栈（traceId 排障）。 |
| 租户隔离（ORM 层） | `MybatisPlusConfig.TenantLineInnerInterceptor` | 所有 SQL 自动追加 `AND tenant_id = ?`，忘写也拦截；平台表 `@InterceptorIgnore` 绕过。 |
| 公共字段自动填充 | `MybatisPlusConfig.MetaObjectHandler` | createdAt/updatedAt/createdBy/updatedBy 自动填充，userId 从 TraceContext 取。 |
| 权限切面 | `PermAspect` + `@RequirePerm` | 后端强制垂直越权拦截（非前端隐藏按钮），perms 来自网关透传头。 |
| 链路追踪 | `TraceContext` + `TraceIdFilter` | traceId/tenantId/userId 贯穿请求→DB→日志 MDC，R 响应体自动携带。 |
| 审计切面 | `AuditLogAspect` + `@AuditLog` | 关键操作（登录/建号/绑 MFA）输出结构化审计日志。 |
| JWT 密钥守卫 | `JwtSecretGuard` | 启动期强校验 JWT_SECRET，拒绝弱密钥/默认值，防自签令牌伪造。 |

---

## 4. 关键类清单

### hris-common（Top 5）

| 类 | 业务定位 |
| --- | --- |
| `R<T>` | 全系统统一响应体，所有模块 Controller 返回值的唯一包装格式。 |
| `BaseEntity` | 租户业务表公共字段基类，定义 id/tenantId/审计字段/逻辑删除/乐观锁的全局规范。 |
| `MybatisPlusConfig` | ORM 层租户隔离兜底 + 公共字段自动填充，是多租户数据隔离的第三道防线（网关验签 → Service 显式传 tenantId → ORM 自动追加条件）。 |
| `PermAspect` + `@RequirePerm` | 后端强制权限点校验，实现垂直越权零容忍（前端隐藏按钮不算，后端必须拦截）。 |
| `TraceContext` | 链路追踪上下文 ThreadLocal，串联 traceId/tenantId/userId 三个横切字段。 |

### hris-gateway（Top 5）

| 类 | 业务定位 |
| --- | --- |
| `JwtAuthGlobalFilter` | 网关核心过滤器：JWT 验签 + 伪造头清洗 + 租户/用户/权限上下文透传，是公网到内网的第一道信任边界。 |
| `JwtTokenVerifier` | JWT HS256 验签器，与 auth 服务共享同一密钥/issuer/claims 字段名，确保网关只信 auth 签发的令牌。 |
| `WhitelistGlobalFilter` | 白名单放行机制，登录/刷新等公开端点无需 JWT 即可访问。 |
| `GatewayErrorWebExceptionHandler` + `GatewayErrorWriter` | WebFlux 侧统一错误响应，把 404/503/504/500 收敛为 R 格式，与 Servlet 侧语义对齐。 |
| `InternalBanWebFilter` | 内部端点封禁：/internal/ 路径公网一律 403，仅允许服务间 Feign 直连。 |

### hris-auth（Top 10）

| 类 | 业务定位 |
| --- | --- |
| `AuthService` | 认证业务编排核心：登录/刷新/登出/me/menus，串联租户校验→用户校验→密码校验→令牌签发。 |
| `TokenService` | JWT 签发/解析/撤销引擎：access/refresh 令牌生成、黑名单、refresh 轮换与重用检测。 |
| `AuthController` | 认证端点入口：登录/刷新/登出/me/menus/mfa-bind/mfa-verify。 |
| `AccountService` | 账号生命周期：内部入职建号（Argon2id 加密 + 角色分配）/ 离职禁用。 |
| `UserAuthService` | 用户授权信息读取：角色/权限点/数据权限，登录时加载后写入 JWT claims。 |
| `AuthUser` / `AuthTenant` | 核心实体：用户账号（密文密码 + MFA 密钥）与租户主档（平台级表）。 |
| `MenuService` | 菜单树派生：由权限点 模块:资源:动作 三级切分生成前端菜单结构。 |
| `MfaService` + `TotpUtil` | MFA（TOTP）能力：绑定生成 Base32 密钥、校验 6 位动态码（±1 步容差）。 |
| `JwtAuthenticationFilter` | auth 服务自身的 JWT 过滤器：解析 Bearer 令牌写入 TraceContext 与 AuthContext。 |
| `AuthTenantMapper` | 租户主档 DAO，@InterceptorIgnore 绕过租户插件（登录时尚无租户上下文）。 |

---

## 5. 多租户与权限模型

### 5.1 tenantId 流转链路

```
登录阶段（无租户上下文）：
  客户端提交 tenantCode
    → AuthService.login 查 AuthTenant.findByCode（@InterceptorIgnore 绕过租户插件）
    → 拿到 tenant.id
    → 设置 TraceContext.setTenantId(tenant.id)
    → 查 AuthUser（此时 SQL 自动追加 tenant_id = 当前 TraceContext 值）
    → 签发 JWT（claims 含 tenant_id）

请求阶段（网关验签后）：
  客户端带 access_token 请求
    → 网关 JwtAuthGlobalFilter 验签
    → 从 claims 取 tenant_id，重写为 X-Tenant-Id 头（丢弃客户端伪造头）
    → 下游服务 TraceIdFilter 读 X-Tenant-Id → TraceContext.setTenantId
    → MybatisPlusConfig.TenantLineInnerInterceptor 从 TraceContext 取 tenantId
    → 所有 SQL 自动追加 AND tenant_id = ?
    → MetaObjectHandler 填充 createdBy/updatedBy（userId 也从 TraceContext 取）

内部调用（Feign）：
  AccountService.create/disable 手动设置 TraceContext.tenantId（无请求头上下文），
  finally 清理，避免 ThreadLocal 串号。
```

### 5.2 权限模型（RBAC + 数据权限）

```
AuthUser（用户）
  └─ AuthUserRole（关联）→ AuthRole（角色）
                              ├─ AuthRolePerm（关联）→ perm_code（模块:资源:动作）
                              └─ AuthDataScope（关联）→ scope_type（SELF/DEPT/DEPT_SUB/CUSTOM/ALL）
                                                         + dept_ids（CUSTOM 时自定义部门集合）
```

**权限点格式**：`模块:资源:动作`，如 `org:headcount:write`、`emp:salary:read`、`cmp:export:approve`。

**三级同源**：菜单（模块→资源）、按钮（动作）、API（@RequirePerm）三处同源派生，保证前端可见菜单 = 后端可访问 API。

**垂直越权拦截**：网关透传 `X-User-Perms`（CSV）→ AuthContextFilter 还原到 AuthContextHolder → PermAspect 拦截 @RequirePerm 方法 → 不含权限点抛 20403。

**数据权限**：`AuthDataScope.scope_type` 控制行级数据可见范围（仅本人/本部门/本部门及下级/自定义部门/全部），由各业务域在查询时自行拼接。

---

## 6. 编译验证结果

> 环境说明：项目要求 Java 21（pom.xml `<java.version>21</java.version>`），系统默认 JDK 17 不兼容；
> 使用系统中已安装的 JDK 25（`D:\Program Files\Java\jdk-25.0.4.1`）执行 `--release 21` 编译。

### 6.1 hris-common

```
$ mvn -pl hris-common -am compile -DskipTests
[INFO] Building hris-common 1.0.0-SNAPSHOT
[INFO] BUILD SUCCESS
```
结果：**BUILD SUCCESS**，target/classes 下生成 20 个 .class 文件。

### 6.2 hris-gateway

```
$ mvn -pl hris-gateway -am compile -DskipTests
[INFO] Reactor Build Order:
[INFO] Building hris-pro 1.0.0-SNAPSHOT                                   [1/3]
[INFO] Building hris-common 1.0.0-SNAPSHOT                                [2/3]
[INFO] Building hris-gateway 1.0.0-SNAPSHOT                               [3/3]
[INFO] BUILD SUCCESS
```
结果：**BUILD SUCCESS**，target/classes 下生成 20 个 .class 文件。

### 6.3 hris-auth

```
$ mvn -pl hris-auth -am compile -DskipTests
[INFO] Reactor Build Order:
[INFO] Building hris-pro 1.0.0-SNAPSHOT                                   [1/3]
[INFO] Building hris-common 1.0.0-SNAPSHOT                                [2/3]
[INFO] Building hris-auth 1.0.0-SNAPSHOT                                   [3/3]
[INFO] Recompiling the module because of changed source code.
[INFO] Compiling 35 source files with javac [debug parameters release 21] to target\classes
[INFO] BUILD SUCCESS
```
结果：**BUILD SUCCESS**，35 个源文件全部编译通过，target/classes 下生成 44 个 .class 文件。

---

## 附：注释改动统计

- **hris-common**：补充方法级 Javadoc 约 15 处（R 静态工厂、BizException 构造器、GlobalExceptionHandler 各分支、AuditLogAspect.around、MybatisPlusConfig 两个 @Bean、AuthContextFilter.doFilterInternal、PermAspect.around、TraceIdFilter.doFilterInternal）。
- **hris-gateway**：补充方法级 Javadoc 约 18 处（GatewayApplication.main、CorsConfig.corsWebFilter、SentinelConfig 三个方法、6 个 Filter 的 filter 方法、GatewayErrorWebExceptionHandler 三个方法、GatewayErrorWriter.resolveTraceId、JwtTokenVerifier.castStrings、SentinelBlockRequestHandler.handleRequest）。
- **hris-auth**：补充方法级 Javadoc 约 30 处（AuthApplication.main、JwtAuthenticationFilter.doFilterInternal、AuthController 全部端点、AccountInternalController 两个端点、AuthService 六个方法、AccountService 三个方法、TokenService 八个方法、MfaService 三个方法、MenuService 两个方法、UserAuthService 四个方法）。
- 所有注释均为新增 Javadoc，未改动任何业务代码（方法签名/import/变量名/字符串字面量均保持原样）。
