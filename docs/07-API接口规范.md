# 07 API 接口规范

> 原则：契约先行（springdoc OpenAPI 3.1 聚合于网关 `/docs`），本文件锁定统一约定 + 各服务核心端点摘要。完整字段级 schema 属实现期产物。

## 1. 通用约定

| 项 | 约定 |
|---|---|
| 前缀 | 公网 `/api/v1/{service}/**`；内部 Feign `/internal/{service}/**`（网关封禁 internal 外部访问） |
| 认证 | `Authorization: Bearer <JWT>`；租户上下文只从 token 解析，Header `X-Tenant-Id` 仅内部透传且验签 |
| 响应体 | `{code, message, data, traceId, timestamp}`；分页 data=`{records[], page, size, total}` |
| 幂等 | 写接口支持 `Idempotency-Key` header（Redis 24h）；流程发起以 bizKey 唯一约束兜底 |
| 版本 | URI 大版本 + 字段向后兼容（只增不改删）；废弃字段标注 `@deprecated` 并公告期 ≥ 1 版本 |
| 时间/金额 | ISO-8601 UTC；金额字符串防精度 |
| 错误 | 见 §2 分段码；4xx 带 `messageKey` 供前端 i18n |

## 2. 错误码分段

| 段 | 含义 |
|---|---|
| 0 | 成功 |
| 10000-10999 | 通用（参数、幂等冲突、限流 10429） |
| 20000-29999 | 认证授权（20401 未认证 / 20403 越权 / 20410 二次认证 / 20501 租户失效） |
| 30000-39999 | 流程（30404 流程不存在 / 30409 状态冲突 / 30423 无权处理任务） |
| 40000-49999 | RAG（40050 模型供应商不可用降级 / 40060 无依据拒答） |
| 5xxxx | 业务域：50 组织 51 员工 52 假勤 53 招聘 54 薪酬 55 绩效（如 51204 编制不足、54001 薪资密域无权限） |
| 50000+ | 系统内部错误（不泄露堆栈，traceId 排障） |

## 3. 各服务核心端点摘要

### auth（登录/租户/权限）
```
POST /api/v1/auth/login (OAuth2.1 password-less 主推授权码+PKCE; 企业对接 SSO)
POST /api/v1/auth/refresh | POST /logout | POST /mfa/bind | POST /mfa/verify
GET  /api/v1/auth/me (用户+权限摘要+租户品牌)
GET  /api/v1/auth/menus | POST /api/v1/auth/second-factor (敏感操作二次认证票据)
内部: POST /internal/auth/accounts (入职建号) | PUT /internal/auth/accounts/{empId}/disable (离职联动)
```

### org
```
GET/POST/PUT /api/v1/org/depts (树查询 ?tree=, 变更走审批后调用生效接口)
POST /api/v1/org/depts/adjust-requests (发起 ORG_ADJUST 流程)
GET /api/v1/org/depts/{id}/snapshot?at=2026-09-01 (历史版本)
CRUD positions/jobs/grades/bands; GET+PUT /headcounts, GET /headcounts/usage
内部: GET /internal/org/hc/check?dept&position&count | GET /internal/org/reportline/{empId}
```

### employee
```
GET /api/v1/emp/employees (花名册多维筛选, 保存视图) | POST 建档 | GET/PUT /{id} (分组字段权限)
POST /api/v1/emp/onboardings | /regularizations | /transfers | /resignations  (各创建生命周期单并发起流程)
GET/POST /api/v1/emp/contracts | PUT /{id}/renew | GET /expiring?days=60
POST /api/v1/emp/employees/{id}/blacklist
内部: GET /internal/emp/{id}/profile-brief | POST /internal/emp/pre-hire-links (扫码履历 token)
```

### attendance
```
POST /api/v1/att/punches (幂等: empId+punchTime 窗口)  ← 打卡洪峰端点(异步落 MQ)
GET  /api/v1/att/my/today | /my/quotas | /calendar?month=
POST /api/v1/att/leaves|overtimes|trips|fixes  (发起+冻结, 联动流程)
PUT  /api/v1/att/leaves/{id}/cancel | /{id}/modify (销假)
管理: shifts 排班/批量, exceptions 工作台, closings 月结 POST/GET, adjustments
内部: GET /internal/att/month-result?cycle&empIds  (薪酬取数)
```

### recruit
```
POST /api/v1/rcs/requisitions(→流程) | openings CRUD | candidates 搜索
POST /api/v1/rcs/resumes/upload (解析回调) | applications 阶段流转 (PATCH stage)
interviews 安排/评价 | offers (生成→流程→发送→回执状态机)
公开: GET /public/rcs/jobs | POST /public/rcs/apply  (限流+验证码)
内部: POST /internal/rcs/convert-to-hire  (→ EMP 入职单)
```

### compensation（全端点默认薪酬密域角色 + 敏感操作二次认证）
```
pay-items/pay-groups/grades CRUD(版本化) | employee-pays (定薪/调薪→流程)
POST /api/v1/cmp/runs (算薪批次 TRIAL→CALC) | GET runs/{id}/diff (环比差异)
POST runs/{id}/review (复核, 与 calc 操作人互斥) | POST runs/{id}/submit-pay (→流程→指令导出)
GET  /api/v1/cmp/my/payslips?period= (工资条, 需二次认证票据)
```

### performance
```
plans/cycles 管理 | POST cycles/{id}/kickoff | my/goals 制定确认链
evaluations (自评/上级/互评提交) | calibrations (九宫格数据/调整)
appeals (→流程) | results 公布查询; train 域: courses/plans/sessions/enrollments/my/credits
```

### flow
```
POST /api/v1/flow/instances (内部为主) | DELETE instances/{id} (撤回)
GET  /api/v1/flow/tasks/todo|done|cc|started (分页聚合)
POST /api/v1/flow/tasks/{id}/approve|reject|transfer|add-sign|withdraw
GET  /api/v1/flow/instances/{id}/track (轨迹图数据: nodes+状态+耗时)
模型: CRUD models, POST models/{id}/deploy, versions
委托: POST delegations (时段)
```

### rag
```
POST /api/v1/rag/chat (SSE: event=token|ref|tool|done|error; body: sessionId?, question, context?)
CRUD /api/v1/rag/sessions, messages/{id}/feedback
知识: spaces/documents (upload presign→confirm), versions publish, reindex POST(异步,taskId)
GET  /api/v1/rag/ops/metrics (看板数据)
内部: POST /internal/rag/index (doc 事件消费兜底手动触发)
```

### platform
```
files: POST presign-upload | GET meta/{id} | download(签名)
notifications: 模板 CRUD, records 查询; reports: defs, exports(异步任务)
dicts CRUD | audit-logs 查询(审计角色) | configs 租户参数
开放API: /openapi/v1/** (API Key + 签名, PLT-F-013)
```

## 4. 事件契约（RocketMQ）

Topic 命名 `hris-{域}-{事件过去式}`；消息体统一信封：
```json
{"eventId":"uuid","topic":"...","tenantId":1,"traceId":"...","occurredAt":"...","bizKey":"...","payload":{...}}
```
约定：At-least-once + 消费幂等表；顺序要求仅限 flow 事件（按 procInstId 队列选择）；
死信 `dlq-{topic}` 告警到平台值班。
