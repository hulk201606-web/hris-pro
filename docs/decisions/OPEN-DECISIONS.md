# 悬而未决决策登记册（OPEN-DECISIONS）

> 记录「定不下来/先放一放/等外部条件」的决策项。只追加 + 就地关闭（OPEN → RESOLVED）。
> 每次 Phase 开始时，把未决项复现到工作上下文最前面，逐条判断能否关闭。

| Date | Source | Open Item | Related Constraints | Current Leaning | Blocked By | Resolves When | Status |
|------|--------|-----------|---------------------|-----------------|------------|---------------|--------|
| 2026-10-16 | Phase 3 一期 | 组织调整申请单表：当前借用 org_dept_history（快照 JSON，kind=ORG_ADJUST_REQUEST）承载，是否新增独立 org_dept_adjust_request 表 | org_db DDL 无 adjust-request 表；一期范围收敛 | 倾向 MVP 借用快照表，二期补独立表 | 等二期 org 深化 | 二期开发前评审 | OPEN |
| 2026-11-05 | Phase 3 一期 | JWT refresh 黑名单/轮换当前进程内内存，多实例需 Redis | 一期单实例部署；08 §1 设计为 Redis 黑名单 | TokenStore 抽象 + RedisTokenStore | 已完成 | 多实例部署前 | RESOLVED（2026-11-05，2454701；注意：网关侧 access 黑名单校验仍未接，见网关行） |
| 2026-11-05 | Phase 3 一期 | flow 服务调用接口（FlowClient Feign）签名未落地 | flow 服务尚未开发；已做降级不阻断 | 内部端点 POST /internal/flow/instances（StartInstanceRequest 契约） | 已完成 | flow 服务开发时 | RESOLVED（2026-11-05，各域 FlowClient/FlowIntegration 已接） |
| 2026-10-16 | Phase 3 一期 | 前端自助端缺 /emp/employees/me 便捷端点（openapi 仅 /{id}） | SELF 语义要求本人数据，前端以 employeeId 定位 | 倾向后端补 /me 端点或确认 X-Self 语义 | 等后端 auth/emp 补端点 | 一期联调前 | OPEN |
| 2026-10-16 | Phase 3 一期 | 菜单接口 MenuNode 缺 route/icon 字段，动态菜单需本地 code→路由/图标注册表 | docs/07 menus 端点契约未细化 | 倾向前端静态菜单 + 预留 loadMenus | 等 auth menus 契约细化 | 二期菜单深化 | OPEN |
| 2026-10-16 | Phase 3 一期 | 前端主包 1.59MB（AntD 全量注册），需按需引入或分包 | Vite 构建 chunk 告警 | 倾向二期 unplugin-vue-components 按需引入 | 等二期前端优化 | 二期 | OPEN |
| 2026-10-16 | Phase 3 网关 | JWT 配置键名不统一（auth 用 auth.jwt.secret，网关用 hris.jwt.secret，读同一 JWT_SECRET 环境变量） | 功能已对齐，键名待统一 | 倾向二期统一为 hris.jwt.* | 等配置收口 | 二期 | OPEN |
| 2026-10-16 | Phase 3 网关 | 登出/禁用黑名单为进程内内存，网关验签不感知，access token 15min TTL 内登出后仍可通行 | docs/08 §1 规划 Redis 共享黑名单 | 倾向二期接 Redis 共享黑名单实现即时失效 | 等 Redis 联调环境 | 多实例部署前 | OPEN |
| 2026-10-16 | Phase 3 网关 | Sentinel 规则 MVP 本地 FlowRule，生产需 Nacos datasource 动态下发 + 精确 IP 限流 | MVP 端点级 QPS 限流 | 倾向二期接 Nacos datasource | 等 Nacos 联调 | 二期 | OPEN |
| 2026-10-16 | Phase 3 网关 | 灰度路由（weight/header）未实现，仅 lb:// 基础转发 | docs/02 §3 规划灰度 | 倾向后续增量实现 | 等灰度需求明确 | 二期 | OPEN |
| 2026-10-16 | Phase 4 收尾 | 敏感字段（身份证/银行卡/手机号）明文落库，脱敏仅在出参；docs/08 要求 KMS 信封加密 | 一期无 KMS；DB 侧无列加密 | 倾向二期引入 KMS 信封加密 + 独立数据密钥 | 等 KMS/密钥管理方案 | 二期安全加固 | OPEN |
| 2026-10-16 | Phase 4 收尾 | JWT 签名算法曾由 jjwt 按密钥长度自动选择（44 字节密钥 → HS384），与 docs/08 的 HS256 描述不一致 | 已临时固定为 HS256；密钥长度门槛仍为 ≥32 字节 | 倾向明确密钥长度策略（HS256→32B / HS512→64B）并写进部署手册 | 等密钥管理规范 | 生产密钥规范评审 | OPEN |
| 2026-10-16 | Phase 4 收尾 | `X-User-Perms` / `X-User-Roles` 用逗号分隔明文透传，权限数量多时有长度与转义风险 | 当前 7 个权限点，未触上限 | 倾向改 JSON 编码并加长度上限 | 等权限点规模评估 | 权限点 >50 时 | OPEN |
| 2026-10-16 | Phase 4 收尾 | RocketMQ 已容器化部署，但业务侧仅落 outbox 表，未接生产者/消费者 | 一期无事件驱动需求 | 二期接入，配合事务消息 | 等二期事件驱动需求 | 二期 | OPEN |
| 2026-10-16 | Phase 4 收尾 | 17 个流程模板一期只产 1 个通用模板 | docs/04 规划 17 个 | 二期按模块补齐 | 等二期流程深化 | 二期 | OPEN |
| 2026-10-16 | Phase 4 收尾 | 缺 Dockerfile / K8s manifest，当前交付形态是 jar + compose 中间件 | 无容器编排交付要求 | 倾向交付前补 Dockerfile + Helm/K8s manifest | 等部署目标环境明确 | 生产交付前 | OPEN |
| 2026-10-16 | Phase 4 收尾 | 无集中日志与链路追踪采集（traceId 已在响应头与响应体输出） | 无可观测性平台 | 接 ELK/Loki + SkyWalking 或 OpenTelemetry | 等可观测性平台 | 生产上线前 | OPEN |
| 2026-10-16 | Phase 5 二期 ATT-F-001/009 | 考勤政策（迟到宽限分钟、迟到重度阈值、打卡围栏 WiFi/IP）无配置表，MVP 用 `AttendanceCalculator` 常量兜底 | att_db DDL 无 att_policy 表；16 张表已冻结 | 倾向二期新增 att_policy 表 + 租户级策略版本化，常量下沉为缺省值 | 等 DDL 变更窗口 | 二期考勤政策深化 | OPEN |
| 2026-10-16 | Phase 5 二期 ATT-F-021 | 法定节假日/调班日历无表，日考勤计算的「法定节假日」优先级分支无法实现 | att_db DDL 无 att_holiday / att_workday_swap 表 | 倾向二期补 att_holiday（含调班日），节假日倍率改由日历驱动 | 等 DDL 变更窗口 | 二期节假日历需求明确时 | OPEN |
| 2026-10-16 | Phase 5 二期 ATT-F-005 | 5,000 TPS 打卡洪峰的 MQ 缓冲未接入，MVP 为同步直写 + 唯一键幂等 | RocketMQ 已容器化但业务侧只落 outbox（见 Phase 4 登记） | 倾向二期接 RocketMQ 事务消息 + 消费者幂等去重 | 等 MQ 生产者/消费者联调 | 压测指标确定后 | OPEN |
| 2026-10-16 | Phase 5 二期 ATT-F-003 | 换班申请无独立表，MVP 通过「重新排班覆盖」实现 | att_db DDL 无 att_shift_swap 表 | 倾向二期补独立换班单表并接审批 | 等 DDL 变更窗口 | 二期排班深化 | OPEN |
| 2026-10-16 | Phase 5 二期 ATT-F-012 | 请假审批期不冻结额度（DDL 的 att_leave_quota 无 frozen_quota 列），仅在审批通过时扣减，存在并发超额风险 | 16 张表已冻结；额度行有乐观锁 version | 倾向二期加 frozen_quota 列，改为「提交冻结 / 通过转扣减 / 驳回解冻」 | 等 DDL 变更窗口 | 二期额度引擎深化 | OPEN |
| 2026-10-16 | Phase 5 二期 ATT-F-012 | 跨年请假的额度按开始日期所在年度归集，未按日切片分摊到两个年度 | 请假单只有单个 biz_key 与 days | 倾向二期按跨年切分生成两条额度流水 | 等额度引擎深化 | 二期额度引擎深化 | OPEN |
| 2026-10-16 | Phase 5 二期 ATT-F-008 | att_daily_attendance.employment_id NOT NULL，但员工域未提供雇佣关系查询内部接口，MVP 落 0 占位 | 员工域 /internal/emp 只有 profile-brief / pre-hire-links | 倾向二期新增 /internal/emp/{id}/employment 接口后回填真实值 | 等员工域内部接口 | 二期跨服务取数评审 | OPEN |
| 2026-10-16 | Phase 5 二期 ATT-F-016 | 加班倍率与加班模式（事前/补录）DDL 无对应列，倍率按开始日派生，模式以 reason 前缀 `[MODE:x]` 承载 | 16 张表已冻结 | 倾向二期加 rate / mode 列，去掉 reason 前缀约定 | 等 DDL 变更窗口 | 二期加班模块深化 | OPEN |
| 2026-10-16 | Phase 5 二期 ATT-F-018 | 外出/居家办公申请（P2）无表，MVP 不实现 | att_db DDL 无对应表 | 倾向二期与出差单合并为「外勤申请」统一模型 | 等 DDL 变更窗口 | 二期外勤需求明确时 | OPEN |
| 2026-10-16 | Phase 5 二期 ATT-F-025 | 报表只按员工维度聚合；部门维度需员工域提供按部门批量取人的内部接口，且 att_daily_attendance 无 dept_id 列 | 员工域内部接口未覆盖；DDL 已冻结 | 倾向二期补 /internal/emp/ids-by-dept 后叠加部门分组 | 等员工域内部接口 | 二期报表深化 | OPEN |
| 2026-10-16 | Phase 5 二期 ATT-F-026 | 长期异常预警（连续旷工/频繁迟到）无通知通道，MVP 只提供异常查询接口 | platform 通知能力未与 ATT 联调 | 倾向二期接 platform 通知模板 + 定时任务 | 等 platform 通知联调 | 二期预警需求明确时 | OPEN |
| 2026-10-17 | Phase 2 招聘/薪酬/绩效/知识库 | 二期 4 个模块（recruit/cmp/prf/rag）代码由 `scripts/gen_phase2.py` 解析 `docs/db/schema-mysql.sql` 的 DDL 自动生成（实体/映射/服务/控制器/DTO），与既有 49 张表零字段偏差 | 工期约束 + 限流窗口；复用考勤已验证范式 | 已交付并编译通过；后续 DDL 变更需重跑生成器并复核 | 等 DDL 变更窗口 | DDL 变更时 | RESOLVED |
| 2026-10-17 | Phase 2 招聘→员工 | 招聘 Offer 通过后无「入职落 employee 域」的内部联动，MVP 仅 CRUD，offer→emp 入职流程未接 | 员工域 /internal/emp 已存在 profile-brief / pre-hire-links；缺 onboarding 接口 | 已交付：员工域新增 `POST /internal/emp/pre-create`（PreCreateRequest + EmployeeService.preCreate）+ recruit `OfferOnboardService` 经 Feign 调用（offer→application→candidate→预建档，幂等） | 等 Nacos 联调验证 | 二期跨服务联动评审 | RESOLVED |
| 2026-10-17 | Phase 2 薪酬取数 | 薪资核算（cmp_payroll_run）需取考勤明细与员工基础信息，但 cmp 模块未生成内部端点，MVP 未接考勤/员工取数 | att/emp 内部端点已存在（/internal/att、/internal/emp） | 已交付：compensation 新增 `CompensationFeignConfig` + `EmployeeClient` + `AttendanceClient` + `PayrollPreviewService`/`Controller`，组合员工主档与考勤月结/日明细（跨域算薪取数） | 等 Nacos 联调验证 | 二期薪酬核算深化 | RESOLVED |
| 2026-10-17 | Phase 2 知识库 RAG | kb 模块当前仅文档/切片/空间/问答日志的 CRUD 管理；语义检索与 LLM 问答未实现（缺向量库 Milvus 接入 + Spring AI Alibaba 管线，docs/05 规划） | docker-compose 已含 milvus/etcd/minio/elasticsearch（profiles=rag） | 阶段 A 已交付：Milvus 单容器（内嵌 etcd+本地存储，绕开 MinIO 镜像迁出 Docker Hub 问题）+ `VectorStore` 可切换抽象 + `IndexRebuildService` 索引重建骨架（chunk→Milvus→milvus_id 回填，占位向量），端到端实测通过。阶段 B/C（embedding+混合检索+问答）依赖 DashScope API Key + Spring AI Alibaba×Boot4 兼容验证 | 阶段 A 已闭环，阶段 B 待 API Key | 二期 RAG 深化 | 部分 RESOLVED（阶段 A）/ 阶段 B 仍 OPEN |
| 2026-10-18 | Phase 2 知识库 RAG（阶段 B） | 承接上条：阶段 B 已交付——ADR-005 修订为 Spring AI 2.0.0 GA（SAA 停更且仅 Boot 3.5），通义千问 + text-embedding-v4 走 DashScope OpenAI 兼容端点；EmbeddingService 降级设计（无 Key 占位向量）+ 密域/索引修复，无 Key 全链路实测通过 | 阶段 C（混合检索/Rerank/LLM 问答）需 DashScope API Key 才能端到端验证 | 阶段 A/B 已 RESOLVED；阶段 C 待 Key | 等用户提供 DASHSCOPE_API_KEY | 拿到 Key 后 | 部分 RESOLVED（A+B）/ 阶段 C 仍 OPEN |
| 2026-10-17 | Phase 2 薪酬密域 | cmp 模块统一用 CMP_ERROR 兜底权限，未把 `CMP_SALARY_FORBIDDEN`（薪酬密域无权限）下沉到工资单/薪酬项明细的专用读端点 | ErrorCode 已含 CMP_SALARY_FORBIDDEN；MVP 全量 CRUD 暴露薪酬数据 | 倾向对 payslip/pay_item 明细补专用敏感读权限点 | 等薪酬密级规范 | 二期薪酬密域评审 | OPEN |
| 2026-10-18 | Phase 2 薪酬密域 | 承接上条：已下沉——4 个实体（payslip/payroll_detail/employee_pay/tax_deduction）的 7 个密文字段（*Cipher）从通用 Response 移除（仅掩码可读）；新增 `SalarySecretController`（/api/v1/cmp/salary-secret/*）+ 新权限点 `cmp:salary:read`，双层语义（控制器挂实体读权限挡 20403，服务层校验密域权限抛 54001）；gen_phase2 加固（*_cipher 列不进 Response）；seed_rbac 修复两处 bug（ON DUPLICATE 补 deleted=0、识别 PERM_* 常量） | 三层实测：有权限取密文 / 无权限 54001 / 通用读无 cipher | 无需阻塞 | 已关闭 | RESOLVED（2026-10-18） |
| 2026-10-17 | Phase 2 命名 | 自动生成的控制器的复数路径为简单加 s（如 import-batchs、talent-pool-tags、doc-versions 不规则复数），属 API 路径瑕疵 | 生成器规则简单 | 倾向后续手动修正不规则复数路径 | 等前端联调 | 联调前 | OPEN |
| 2026-10-18 | Phase 2 命名 | 承接上条：实际仅 `import-batchs` 一处不规则（-ch 结尾应加 -es；talent-pool-tags/doc-versions 经核验本就正确） | 生成器 plural 规则缺 -es 分支 | 已修复：gen_phase2.py 新增 `plural()`（-s/-x/-z/-ch/-sh → -es），重生成 ImportBatchController，回归核验 49 表仅此一例变化；前端无引用 | 无需阻塞 | 已关闭 | RESOLVED（2026-10-18） |
| 2026-10-17 | Phase 2 跨域 Feign 范式 | recruit/cmp/prf/rag 4 个模块补齐内部 Feign 调用范式（`*FeignConfig` + `*Client` + 透传 X-Tenant-Id/X-Trace-Id），与一期 employee/org/att/flow 既有 Feign 完全一致；新增 4 个 `@RequirePerm` 公网端点 + 4 条 admin 授权 | 一期已确立内部端点封禁（网关 InternalBanWebFilter）+ 服务间签名头约定 | 已交付并编译通过；运行时需 Nacos 注册中心就绪 | 等 Nacos 联调 | 二期联调 | RESOLVED |
