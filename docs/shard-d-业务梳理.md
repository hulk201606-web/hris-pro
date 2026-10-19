# 智云 HR 多租户 SaaS 系统 —— Shard D 业务梳理

> 覆盖范围：hris-flow（流程中台）、hris-rag（AI 知识库）、hris-platform（平台基础服务）、frontend（前端 SPA）
> 生成日期：2026-10-19

---

## 1. 模块总览

| 工程 | 职责 | 业务域 | 技术栈 |
|------|------|--------|--------|
| **hris-flow** | 基于 Flowable 的统一流程引擎中台，为请假/加班/报销/入转调离等业务单提供流程定义部署、实例发起、任务审批/驳回/转办/加签/撤回、轨迹查询、事件出站 | Flowable 流程中台 | Spring Boot + Flowable 8 + MyBatis-Plus + MySQL |
| **hris-rag** | AI 知识库与语义检索：文档上传 → 解析 → 切片 → Embedding → Milvus 向量索引 → 语义召回 → 大模型拼装；含知识空间、ACL 标签、评测集、治理工单 | 知识库与语义检索 | Spring Boot + Spring AI + Milvus + MyBatis-Plus |
| **hris-platform** | 平台基础服务：MinIO 文件存储（预签名直传）、数据字典、站内通知（模板 + 记录 + Outbox）、审计日志、报表定义、租户配置、异步导出任务 | 平台基础服务 | Spring Boot + MinIO + MyBatis-Plus + MySQL |
| **frontend** | 统一 SPA：Vue 3 + TypeScript + Ant Design Vue + Pinia + Vue Router，单应用内通过路由前缀 `/self`（员工自助端）与 `/admin`（管理端）区分双端 | 前端 SPA | Vue 3 + Vite + Pinia + Ant Design Vue |

---

## 2. 后端包结构表

### 2.1 hris-flow（41 个 Java 文件）

| 全限定类名 | 分层 | 一句话职责 |
|------------|------|-----------|
| com.hris.flow.FlowApplication | 启动类 | Spring Boot 入口，扫描 com.hris 根包 |
| com.hris.flow.client.OrgClient | Feign 客户端 | 调用 hris-org 解析审批人/汇报线 |
| com.hris.flow.config.FlowableManualConfig | 配置 | Boot 4.0 降级预案：手动装配 ProcessEngine |
| com.hris.flow.config.FlowFeignConfig | 配置 | Feign 拦截器注入 tenantId/userId |
| com.hris.flow.controller.FlowInternalController | Controller | 内部回调端点（业务模块审批完成后回写） |
| com.hris.flow.controller.InstanceController | Controller | 流程实例发起/撤回/轨迹 |
| com.hris.flow.controller.ModelController | Controller | 流程模型（BPMN）部署/草稿 CRUD |
| com.hris.flow.controller.TaskController | Controller | 待办/已办/抄送/我发起 + 审批操作 |
| com.hris.flow.dto.ModelDtos | DTO | 流程模型请求/响应记录 |
| com.hris.flow.dto.PageResult | DTO | 通用分页结果（record） |
| com.hris.flow.dto.StartInstanceRequest | DTO | 发起流程请求（bizType/bizKey/variables） |
| com.hris.flow.dto.StartInstanceResponse | DTO | 发起流程响应（instanceId/status） |
| com.hris.flow.dto.TaskActionRequest | DTO | 审批操作请求（comment/attachments/targetUserId） |
| com.hris.flow.dto.TaskView | DTO | 待办/已办列表项 |
| com.hris.flow.dto.TrackNode | DTO | 流程轨迹节点 |
| com.hris.flow.dto.TrackResponse | DTO | 流程轨迹图数据 |
| com.hris.flow.entity.BizRelation | 实体 | 业务单 ↔ 流程实例映射（flow_ext_biz_relation） |
| com.hris.flow.entity.EventOutbox | 实体 | 事件出站表（事务消息兜底） |
| com.hris.flow.entity.FlowModel | 实体 | 流程模型草稿/部署记录（flow_ext_model） |
| com.hris.flow.entity.NodeForm | 实体 | 节点表单配置 |
| com.hris.flow.entity.TaskAction | 实体 | 任务操作留痕（flow_ext_task_action） |
| com.hris.flow.entity.TimeoutCfg | 实体 | 任务超时配置 |
| com.hris.flow.enums.DeployState | 枚举 | 模型部署状态 DRAFT/DEPLOYED |
| com.hris.flow.enums.InstanceStatus | 枚举 | 实例状态 RUNNING/FINISHED/TERMINATED |
| com.hris.flow.enums.OutboxState | 枚举 | Outbox 消息状态 PUBLISHED/CONSUMED |
| com.hris.flow.enums.TaskActionType | 枚举 | 审批动作 APPROVE/REJECT/TRANSFER/ADD_SIGN/WITHDRAW |
| com.hris.flow.mapper.BizRelationMapper | Mapper | 业务关系 CRUD |
| com.hris.flow.mapper.EventOutboxMapper | Mapper | 事件出站 CRUD |
| com.hris.flow.mapper.FlowModelMapper | Mapper | 流程模型 CRUD |
| com.hris.flow.mapper.NodeFormMapper | Mapper | 节点表单 CRUD |
| com.hris.flow.mapper.TaskActionMapper | Mapper | 任务操作留痕 CRUD |
| com.hris.flow.mapper.TimeoutCfgMapper | Mapper | 超时配置 CRUD |
| com.hris.flow.service.AssigneeResolver | Service | 审批人解析链（指定人 → 汇报线 → 角色） |
| com.hris.flow.service.BizRelationService | Service | 业务关系查询/状态回写 |
| com.hris.flow.service.EventOutboxService | Service | 事件发布（事务消息，兜底最终一致性） |
| com.hris.flow.service.InstanceService | Service | 流程实例发起（幂等）/撤回/轨迹 |
| com.hris.flow.service.ModelService | Service | 流程模型部署（BPMN XML → Flowable 部署） |
| com.hris.flow.service.ReconcileService | Service | 对账：定时扫描 Outbox 未消费消息重投 |
| com.hris.flow.service.TaskActionService | Service | 任务审批/驳回/转办/加签/撤回 |
| com.hris.flow.service.TaskQueryService | Service | 待办/已办/抄送/我发起分页查询 |
| com.hris.flow.util.JsonUtil | 工具 | JSON 序列化/反序列化 |

### 2.2 hris-rag（61 个 Java 文件）

| 全限定类名 | 分层 | 一句话职责 |
|------------|------|-----------|
| com.hris.rag.RagApplication | 启动类 | Spring Boot 入口 |
| com.hris.rag.client.EmployeeClient | Feign 客户端 | 调用 hris-employee 拉员工档案简报 |
| com.hris.rag.config.MilvusProperties | 配置 | Milvus 连接参数（host/port/dimension/集合名） |
| com.hris.rag.config.RagFeignConfig | 配置 | Feign 拦截器注入 tenantId/userId |
| com.hris.rag.controller.AclTagController | Controller | ACL 标签 CRUD |
| com.hris.rag.controller.ChunkController | Controller | 文档切片 CRUD |
| com.hris.rag.controller.DocumentController | Controller | 文档 CRUD |
| com.hris.rag.controller.DocVersionController | Controller | 文档版本 CRUD |
| com.hris.rag.controller.EvalCaseController | Controller | 评测用例 CRUD |
| com.hris.rag.controller.EvalRunController | Controller | 评测运行记录查询 |
| com.hris.rag.controller.EvalSetController | Controller | 评测集 CRUD |
| com.hris.rag.controller.GovernTicketController | Controller | 治理工单 CRUD |
| com.hris.rag.controller.IndexRebuildController | Controller | 触发索引重建 |
| com.hris.rag.controller.KnowledgeScopeController | Controller | 知识可见范围查询（跨域取员工档案） |
| com.hris.rag.controller.SpaceController | Controller | 知识空间 CRUD |
| com.hris.rag.controller.SpaceMemberController | Controller | 空间成员 CRUD |
| com.hris.rag.dto.*（11 个 DTO） | DTO | 各实体的请求/响应 record |
| com.hris.rag.entity.AclTag | 实体 | ACL 权限标签 |
| com.hris.rag.entity.Chunk | 实体 | 文档切片（chunk 文本 + 向量外键） |
| com.hris.rag.entity.Document | 实体 | 文档元数据（标题/文件/类型/指纹） |
| com.hris.rag.entity.DocVersion | 实体 | 文档版本历史 |
| com.hris.rag.entity.EvalCase | 实体 | 评测用例（问题 + 标准答案） |
| com.hris.rag.entity.EvalRun | 实体 | 评测运行记录 |
| com.hris.rag.entity.EvalSet | 实体 | 评测集 |
| com.hris.rag.entity.GovernTicket | 实体 | 治理工单（低质内容/过期知识处理） |
| com.hris.rag.entity.Space | 实体 | 知识空间 |
| com.hris.rag.entity.SpaceMember | 实体 | 空间成员授权 |
| com.hris.rag.mapper.*（10 个 Mapper） | Mapper | 各实体 MyBatis-Plus Mapper |
| com.hris.rag.service.AclTagService | Service | ACL 标签 CRUD |
| com.hris.rag.service.ChunkService | Service | 切片 CRUD（含触发 Embedding + 向量写入） |
| com.hris.rag.service.DocumentService | Service | 文档 CRUD |
| com.hris.rag.service.DocVersionService | Service | 文档版本 CRUD |
| com.hris.rag.service.EmbeddingService | Service | 文本向量化（Spring AI EmbeddingModel → DashScope text-embedding-v4） |
| com.hris.rag.service.EvalCaseService | Service | 评测用例 CRUD |
| com.hris.rag.service.EvalRunService | Service | 评测运行 CRUD |
| com.hris.rag.service.EvalSetService | Service | 评测集 CRUD |
| com.hris.rag.service.GovernTicketService | Service | 治理工单 CRUD |
| com.hris.rag.service.IndexRebuildService | Service | 索引重建：全量重新切片 + Embedding + 写 Milvus |
| com.hris.rag.service.KnowledgeScopeService | Service | 知识可见性范围计算（按员工部门/汇报线过滤） |
| com.hris.rag.service.SpaceMemberService | Service | 空间成员 CRUD |
| com.hris.rag.service.SpaceService | Service | 知识空间 CRUD |
| com.hris.rag.vector.MilvusServerStore | 向量存储 | Milvus 直连实现：建集合/索引/写入/检索/删除 |
| com.hris.rag.vector.VectorStore | 向量存储 | 向量存储抽象接口 |

### 2.3 hris-platform（50 个 Java 文件）

| 全限定类名 | 分层 | 一句话职责 |
|------------|------|-----------|
| com.hris.platform.PlatformApplication | 启动类 | Spring Boot 入口 |
| com.hris.platform.config.MinioProperties | 配置 | MinIO 连接参数 |
| com.hris.platform.controller.AuditLogController | Controller | 审计日志查询 |
| com.hris.platform.controller.ConfigController | Controller | 租户配置 CRUD |
| com.hris.platform.controller.DictController | Controller | 字典类型/字典项查询 |
| com.hris.platform.controller.FileController | Controller | 文件上传预签名/下载/元数据 |
| com.hris.platform.controller.NotificationController | Controller | 通知模板/记录查询 |
| com.hris.platform.controller.ReportController | Controller | 报表定义 CRUD |
| com.hris.platform.dto.*（7 个 DTO） | DTO | 各实体请求/响应 record |
| com.hris.platform.entity.SysAuditLog | 实体 | 审计日志 |
| com.hris.platform.entity.SysDictItem | 实体 | 字典项 |
| com.hris.platform.entity.SysDictType | 实体 | 字典类型 |
| com.hris.platform.entity.SysExportTask | 实体 | 异步导出任务 |
| com.hris.platform.entity.SysFileMeta | 实体 | 文件元数据 |
| com.hris.platform.entity.SysNotifyRecord | 实体 | 通知发送记录 |
| com.hris.platform.entity.SysNotifyTemplate | 实体 | 通知模板 |
| com.hris.platform.entity.SysOutboxMessage | 实体 | Outbox 消息表 |
| com.hris.platform.entity.SysReportDef | 实体 | 报表定义 |
| com.hris.platform.entity.SysSchedulerTaskRegistry | 实体 | 定时任务注册表 |
| com.hris.platform.entity.SysTenantConfig | 实体 | 租户配置 |
| com.hris.platform.mapper.*（11 个 Mapper） | Mapper | 各实体 MyBatis-Plus Mapper |
| com.hris.platform.service.AuditLogService | Service | 审计日志写入/查询 |
| com.hris.platform.service.DictItemService | Service | 字典项 CRUD |
| com.hris.platform.service.DictTypeService | Service | 字典类型 CRUD |
| com.hris.platform.service.ExportTaskService | Service | 异步导出任务创建/查询 |
| com.hris.platform.service.FileService | Service | MinIO 预签名直传/下载/元数据 |
| com.hris.platform.service.NotifyRecordService | Service | 通知记录查询/落库（MVP 渠道投递 stub） |
| com.hris.platform.service.NotifyTemplateService | Service | 通知模板 CRUD |
| com.hris.platform.service.OutboxService | Service | Outbox 消息发布/消费 |
| com.hris.platform.service.ReportService | Service | 报表定义 CRUD |
| com.hris.platform.service.TenantConfigService | Service | 租户配置查询/删除 |
| com.hris.platform.storage.MinioStorage | 存储 | MinIO SDK 封装（presignPut/presignGet） |
| com.hris.platform.util.CodeUtil | 工具 | 编码生成 |
| com.hris.platform.util.JsonUtil | 工具 | JSON 序列化 |

---

## 3. 核心业务流程

### 3.1 流程中台（hris-flow）

**Flowable 8 复用方式**：所有业务模块（请假/加班/补卡/报销/入转调离）不各自嵌入 Flowable，而是统一调用 hris-flow 的 API 发起流程实例。业务单的 `bizType`（如 `ATT_LEAVE`、`ATT_OT`）作为流程定义 Key，`bizKey` 作为业务单号。

**关键 API**：

| API | 用途 |
|-----|------|
| `POST /api/v1/flow/instances` | 发起流程实例（幂等：同 tenant+bizType+bizKey 唯一） |
| `POST /api/v1/flow/instances/{id}/withdraw` | 撤回（无已处理节点时允许） |
| `GET /api/v1/flow/instances/{id}/track` | 流程轨迹图（节点 + 状态 + 耗时） |
| `GET /api/v1/flow/tasks/todo` | 我的待办（分页聚合） |
| `GET /api/v1/flow/tasks/done` | 我的已办 |
| `GET /api/v1/flow/tasks/started` | 我发起的 |
| `POST /api/v1/flow/tasks/{id}/approve` | 审批通过 |
| `POST /api/v1/flow/tasks/{id}/reject` | 驳回（MVP 终止回发起人） |
| `POST /api/v1/flow/tasks/{id}/transfer` | 转办 |
| `POST /api/v1/flow/tasks/{id}/add-sign` | 加签（追加候选人并行加签） |

**状态流转**：
```
业务单创建 → InstanceService.start()
  → Flowable startProcessInstanceByKeyAndTenantId()
  → 落 BizRelation（RUNNING）
  → EventOutbox 发布 TOPIC_INSTANCE_STARTED
  → 审批人处理 → TaskActionService.approve()
    → taskService.complete()
    → 末节点时 finishInstance() → BizRelation 更新为 FINISHED
    → EventOutbox 发布 TOPIC_TASK_COMPLETED / TOPIC_INSTANCE_FINISHED
  → 业务模块消费 Outbox 事件，回写业务单状态（如请假单审批通过后扣减假期额度）
```

**多租户隔离**：Flowable 原生 `tenantId` 字段，所有查询强制带 tenantId（来自 JWT，网关层校验）。

**审批人解析链**（AssigneeResolver）：指定审批人 > 汇报线（OrgClient 查直属上级）> 角色/岗位。

### 3.2 RAG 链路（hris-rag）

**完整链路**：
```
文档上传（FileService 预签名直传 MinIO）
  → DocumentService.create() 落文档元数据
  → ChunkService.create() 切片
    → EmbeddingService.embed() 调用 DashScope text-embedding-v4（1024 维）
    → MilvusServerStore.upsert() 写入 Milvus 集合
  → 语义检索：EmbeddingService.embed(query) → MilvusServerStore.search(topK)
  → 召回 chunk IDs → 取 chunk 文本 → 拼装 Prompt 给大模型
```

**Milvus 集合设计**：
- 集合名：由 `hris.rag.milvus.collection` 配置
- Schema：主键 `chunk_id`（Int64）+ 向量字段 `embedding`（FloatVector, 1024 维）
- 动态字段承载 `space_id`、`acl_tags` 等元数据（`enableDynamicField=true`）
- 索引：COSINE 距离 + AUTOINDEX（Milvus 2.5 standalone 免调参）
- 一致性级别：STRONG

**Embedding 模型**：通义千问 `text-embedding-v4`，走 DashScope OpenAI 兼容端点。未配置 `DASHSCOPE_API_KEY` 时降级到确定性占位向量（由 seed 派生），保证链路可验证。

**知识可见性**：KnowledgeScopeService 按员工 ID 拉取档案简报（部门/汇报线/在职状态），检索前根据 ACL 标签过滤，避免跨租户/跨部门泄露。

### 3.3 平台服务（hris-platform）

**文件存储**：MinIO 预签名直传模式
- 上传：`POST /api/v1/platform/files/presign-upload` → 返回上传 URL + fileId，前端直传 MinIO
- 对象键：`tenant-{id}/{module}/{yyyyMM}/{uuid}-{safeName}`
- 下载：`POST /api/v1/platform/files/{id}/download` → 返回下载预签名 URL（10 分钟有效）

**数据字典**：字典类型（DictType）→ 字典项（DictItem）二级结构，前端下拉选项数据源。

**通知服务**：
- 模板（NotifyTemplate）：按渠道（站内信/邮件）管理消息模板
- 记录（NotifyRecord）：发送记录落库，MVP 渠道投递为 stub（状态直接置 SENT）
- Outbox（SysOutboxMessage）：事务消息表，兜底最终一致性，后续由 RocketMQ 消费者接管真实投递

---

## 4. 前端架构

### 4.1 目录结构

| 目录 | 承载内容 |
|------|---------|
| `api/` | 后端 API 封装层：http 实例、各业务域请求函数、mock 开关 |
| `components/` | 通用组件：admin CRUD 页面骨架（EntityCrudPage/SimpleCrudPage）、空态、员工上下文面板 |
| `directives/` | 自定义指令：v-perm 按钮级权限控制 |
| `layouts/` | 布局组件：MainLayout（自助端）、AdminLayout（管理端） |
| `router/` | 路由表 + 管理端菜单注册表（admin-menu.ts） |
| `stores/` | Pinia 状态：user store（令牌/用户信息/权限点/菜单） |
| `theme/` | Ant Design 主题定制 |
| `types/` | TypeScript 类型定义（admin.d.ts、api.d.ts） |
| `views/` | 页面组件：self/（自助端）、admin/（管理端）、login/ |

### 4.2 双端设计

单 SPA 内通过路由前缀区分双端：

| 端 | 路由前缀 | 布局 | 目标用户 | 菜单来源 |
|----|---------|------|---------|---------|
| 员工自助端 | `/self/*` | MainLayout | 普通员工 | 静态配置（后续菜单树驱动） |
| 管理端 | `/admin/*` | AdminLayout | HR 管理员 | 后端 `GET /auth/menus` 菜单树 + admin-menu.ts 注册表派生 |

**权限控制**：
- 路由守卫：未登录跳 `/login`
- 按钮级：`v-perm="'att:daily:compute'"` 指令 + `userStore.hasPerm()` 判断
- 管理端菜单：后端权限点 `module:resource:action` 按资源段（第二段）匹配注册表，未命中不展示

### 4.3 主要 views 页面与后端业务域

| 页面路由 | 对应后端业务域 |
|---------|--------------|
| `/self/home` | 自助首页（聚合 flow 待办数） |
| `/self/profile` | hris-employee（员工档案） |
| `/self/leave/apply` | hris-attendance（请假单）+ hris-flow（审批流） |
| `/self/overtime/apply` | hris-attendance（加班单）+ hris-flow |
| `/self/fix/apply` | hris-attendance（补卡单）+ hris-flow |
| `/self/approvals` | hris-flow（待办任务审批） |
| `/admin/recruit/*` | hris-recruit（职位需求/候选人/Offer） |
| `/admin/attendance/*` | hris-attendance（考勤组/班次/假种/日考勤/异常） |
| `/admin/compensation/*` | hris-compensation（薪酬核算/员工薪酬/算薪预览） |
| `/admin/performance/*` | hris-performance（考核周期/目标/评估上下文） |
| `/admin/knowledge/*` | hris-rag（知识空间/文档/知识范围） |

### 4.4 API 封装层组织方式

- `api/http.ts`：统一 axios 实例，请求拦截器注入 Bearer Token，响应拦截器解包 `{code, data, message}` 统一响应体，401 静默续期
- `api/token.ts`：本地令牌存取（localStorage）
- `api/mock.ts` + `*-mock.ts`：Mock 开关，开发环境可切 mock 数据
- 各业务域一个文件：`attendance.ts`、`flow.ts`、`recruit.ts`、`compensation.ts`、`performance.ts`、`knowledge.ts`、`employee.ts`、`auth.ts`
- 每个导出函数上方 `/** ... */` 注释标明 URL + 业务语义

### 4.5 Pinia stores

| Store | 承载状态 |
|-------|---------|
| `useUserStore` | accessToken / refreshToken / userInfo / roles / perms（权限点数组）/ tenant（品牌）/ menus（菜单树）/ loaded 标志；getter: isAuthenticated、hasPerm |

---

## 5. 前后端联动总览

**关键调用路径**：

```
员工发起请假：
  LeaveApplyView.vue
    → POST /api/v1/att/leave-requests（hris-attendance）
    → attendance 内部调 POST /api/v1/flow/instances（hris-flow）发起审批
    → 审批人在 MyApprovalsView.vue 看到待办
    → POST /api/v1/flow/tasks/{id}/approve（hris-flow）
    → flow 通过 Outbox 通知 attendance 回写请假单状态

管理员查看考勤：
  DailyAttendanceView.vue
    → GET /api/v1/att/daily（hris-attendance）
    → POST /api/v1/att/daily/compute 触发重算

知识库管理：
  DocumentListView.vue
    → CRUD /api/v1/kb/documents（hris-rag）
    → ChunkService 自动切片 + Embedding + 写 Milvus
```

**认证链路**：
- 登录：`POST /api/v1/auth/login` → 返回 accessToken + refreshToken
- 后续请求：Authorization: Bearer {accessToken}
- 401 自动：`POST /api/v1/auth/refresh` 静默续期，失败跳登录页

---

## 6. 编译验证结果

| 模块 | 编译命令 | 结果 |
|------|---------|------|
| hris-flow | `mvn -pl hris-flow -am clean compile -DskipTests -Djava.version=17` | BUILD SUCCESS（41 源文件） |
| hris-rag | `mvn -pl hris-rag -am clean compile -DskipTests -Djava.version=17` | BUILD SUCCESS（61 源文件） |
| hris-platform | `mvn -pl hris-platform -am clean compile -DskipTests -Djava.version=17` | BUILD SUCCESS（50 源文件） |

> 注：项目 pom 配置 `java.version=21`，本地环境为 JDK 17，编译时通过 `-Djava.version=17` 覆盖。代码本身使用 record（Java 16+）等特性，JDK 17 兼容。

---

## 7. 本次注释改动统计

| 类别 | 数量 |
|------|------|
| Java 类级 Javadoc（原本已有，本次审计确认齐全） | 152/152 |
| Java 方法级 Javadoc 新增（hris-rag + hris-platform service） | ~80 处 |
| 前端文件级块注释新增 | 33 个文件 |
| 前端 API 函数 Javadoc 新增 | 44 个函数 |
| 编译验证 | 3/3 模块 BUILD SUCCESS |
