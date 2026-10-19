# Shard-B 业务梳理：组织主数据 / 员工档案与生命周期 / 招聘流程

> 本文档对应智云 HR 多租户 SaaS 系统人事主数据域的三个后端模块：`hris-org`、`hris-employee`、`hris-recruit`。
> 本次工作范围：仅对三个模块 `src/main/java/**/*.java`（共 178 个主代码文件）补充中文 Javadoc 注释，未改动任何业务代码、方法签名、import 与字符串字面量。

---

## 1. 模块总览

| 模块 | 业务域 | 职责 | 主要包 |
| --- | --- | --- | --- |
| **hris-org** | 组织主数据 | 维护法人主体 → 部门树 → 职务/岗位/职级/职级带的组织骨架，以及部门×岗位×季度的编制计划；对外提供编制校验与汇报链解析供其他域调用 | controller / service / entity / mapper / dto / client / config / util |
| **hris-employee** | 员工档案与生命周期 | 员工主档（花名册、证件/手机号密文+掩码、敏感字段 diff 审计）、雇佣关系、合同台账、入职/转正/调动/离职生命周期单与状态机、黑名单；通过内部端点供组织域与招聘域跨域调用 | controller / service / entity / mapper / dto / enums / integration / client / util |
| **hris-recruit** | 招聘流程 | 招聘需求 → 职位发布 → 候选人/人才库 → 投递（Application）→ 面试安排与评价 → Offer → 录用后跨域触发员工预建档；含招聘渠道与阶段流转日志 | controller / service / entity / mapper / dto / client / config |

三个模块均为 Spring Boot + MyBatis-Plus 的独立微服务，通过 OpenFeign 互调（`/internal/**` 端点由网关封禁外部访问，配合双向签名）。

---

## 2. 包结构表

### 2.1 hris-org（50 个文件）

| 全限定类名 | 分层 | 一句话职责 |
| --- | --- | --- |
| com.hris.org.OrgApplication | 启动类 | 组织域 Spring Boot 入口 |
| com.hris.org.client.EmployeeClient | Feign Client | 调用员工域档案简报（汇报线解析用） |
| com.hris.org.client.FlowClient | Feign Client | 调用工作流引擎发起组织调整流程 |
| com.hris.org.config.OrgFeignConfig | 配置 | Feign 拦截器/双向签名配置 |
| com.hris.org.controller.LegalEntityController | Controller | 法人主体端点（查询/新建/更新） |
| com.hris.org.controller.DeptController | Controller | 部门树查询/新建/更新/历史快照/组织调整申请 |
| com.hris.org.controller.JobController | Controller | 职务字典端点 |
| com.hris.org.controller.PositionController | Controller | 岗位字典端点 |
| com.hris.org.controller.GradeController | Controller | 职级字典端点 |
| com.hris.org.controller.GradeBandController | Controller | 职级带字典端点 |
| com.hris.org.controller.HeadcountController | Controller | 编制查询/设定/占用看板 |
| com.hris.org.controller.OrgInternalController | Controller | 内部端点：编制校验 hc/check、汇报链解析 reportline |
| com.hris.org.service.LegalEntityService | Service | 法人主体 CRUD（USCC 大写、默认 CNY） |
| com.hris.org.service.DeptService | Service | 部门树、物化路径、版本化生效、历史快照、组织调整申请 |
| com.hris.org.service.JobService | Service | 职务字典 CRUD（编码唯一校验） |
| com.hris.org.service.PositionService | Service | 岗位字典 CRUD |
| com.hris.org.service.GradeService | Service | 职级 CRUD（可挂职级带） |
| com.hris.org.service.GradeBandService | Service | 职级带 CRUD |
| com.hris.org.service.HeadcountService | Service | 编制额度设定、占用看板、季度编制校验 |
| com.hris.org.service.ReportLineService | Service | 直属上级 + 部门负责人逐级向上解析汇报链 |
| com.hris.org.service.FlowIntegration | Service(集成) | 组织调整流程发起（MVP 降级不阻断） |
| com.hris.org.entity.OrgLegalEntity | Entity | 法人主体表 org_legal_entity |
| com.hris.org.entity.OrgDept | Entity | 部门表 org_dept（物化路径 path、版本生效） |
| com.hris.org.entity.OrgDeptHistory | Entity | 部门历史快照表 org_dept_history |
| com.hris.org.entity.OrgJob | Entity | 职务字典 |
| com.hris.org.entity.OrgPosition | Entity | 岗位字典 |
| com.hris.org.entity.OrgGrade | Entity | 职级 |
| com.hris.org.entity.OrgGradeBand | Entity | 职级带（薪酬带宽分组） |
| com.hris.org.entity.OrgHeadcount | Entity | 编制计划（部门+岗位+季度三元组） |
| com.hris.org.entity.OrgHcOccupancy | Entity | 编制占用明细回写表 |
| com.hris.org.mapper.*（9 个） | Mapper | 上述实体对应 MyBatis-Plus Mapper |
| com.hris.org.dto.*（10 个） | DTO | CatalogRequest/Response、DeptNode、DeptRequest、AdjustRequest、Headcount*、HcCheck |
| com.hris.org.util.CodeUtil | Util | 编码归一化与序列生成（d/pos/job/grade/band 前缀） |
| com.hris.org.util.JsonUtil | Util | 快照 JSON 序列化/反序列化 |

### 2.2 hris-employee（68 个文件）

| 全限定类名 | 分层 | 一句话职责 |
| --- | --- | --- |
| com.hris.employee.EmployeeApplication | 启动类 | 员工域入口 |
| com.hris.employee.client.AuthClient | Feign Client | 调用认证域创建默认账号 |
| com.hris.employee.client.FlowClient | Feign Client | 调用工作流引擎 |
| com.hris.employee.client.OrgClient | Feign Client | 调用组织域校验 |
| com.hris.employee.config.EmployeeFeignConfig | 配置 | Feign 配置 |
| com.hris.employee.controller.EmployeeController | Controller | 花名册/建档/详情/更新/黑名单 |
| com.hris.employee.controller.LifecycleController | Controller | 入职/转正/调动/离职四类生命周期单发起 |
| com.hris.employee.controller.ContractController | Controller | 合同台账/新签/续签/到期预警 |
| com.hris.employee.controller.EmployeeInternalController | Controller | 内部端点：档案简报、pre-hire 链接、候选人预建档 |
| com.hris.employee.service.EmployeeService | Service | 员工主档建档、花名册、档案更新、敏感字段 diff 审计、黑名单、预建档、简报 |
| com.hris.employee.service.EmployeeStateMachine | Service | 员工状态机：统一校验合法流转并落状态流水 |
| com.hris.employee.service.OnboardingService | Service | 入职办理（编制校验 + EMP_ONBOARD 流程 + 初始账号载荷） |
| com.hris.employee.service.RegularizationService | Service | 转正办理（试用→正式，EMP_REGULAR） |
| com.hris.employee.service.TransferService | Service | 调动办理（在职 + 目标编制校验 + EMP_TRANSFER） |
| com.hris.employee.service.ResignationService | Service | 离职办理（在职/试用 + EMP_RESIGN + 最后工作日） |
| com.hris.employee.service.ContractService | Service | 合同台账、续签、到期预警 |
| com.hris.employee.service.EmploymentService | Service | 雇佣关系（当前在职记录查询） |
| com.hris.employee.service.LifecycleCaseService | Service | 生命周期单落库与响应组装 |
| com.hris.employee.service.PendingTransactionService | Service | 待生效事务（调动/离职到期落账） |
| com.hris.employee.service.PreHireService | Service | 待入职自助办理链接生成 |
| com.hris.employee.service.ProfileService | Service | 教育/工作/家庭/紧急联系人子档案替换式维护 |
| com.hris.employee.service.EmpNoService | Service | 工号序列生成（按前缀） |
| com.hris.employee.integration.AccountIntegration | 集成 | 账号创建联动 |
| com.hris.employee.integration.FlowIntegration | 集成 | 流程引擎集成 |
| com.hris.employee.integration.HeadcountIntegration | 集成 | 调用组织域编制校验 |
| com.hris.employee.entity.Employee | Entity | 员工主档 emp_employee（密文+掩码双列） |
| com.hris.employee.entity.Employment | Entity | 雇佣关系（法人/部门/岗位/职级/直属上级/生效日） |
| com.hris.employee.entity.Contract | Entity | 合同台账 |
| com.hris.employee.entity.LifecycleCase | Entity | 生命周期单 |
| com.hris.employee.entity.StatusLog | Entity | 状态流转流水 |
| com.hris.employee.entity.ChangeAudit | Entity | 敏感字段变更 diff 审计 |
| com.hris.employee.entity.PendingTransaction | Entity | 待生效事务 |
| com.hris.employee.entity.ProfileEducation/Work/Family/Emergency | Entity | 四类子档案 |
| com.hris.employee.enums.EmployeeStatus | Enum | 员工八态状态机（PENDING/PROBATION/ACTIVE/RESIGNING/RESIGNED/CANCELLED/RETIRED/BLACKLISTED） |
| com.hris.employee.enums.ContractType | Enum | 合同类型 |
| com.hris.employee.enums.LifecycleType | Enum | 生命周期单类型（ONBOARD/REGULAR/TRANSFER/RESIGN…） |
| com.hris.employee.enums.PendingTransType | Enum | 待生效事务类型 |
| com.hris.employee.mapper.*（11 个） | Mapper | 上述实体对应 Mapper |
| com.hris.employee.dto.*（15 个） | DTO | EmployeeDtos/OnboardRequest/ResignRequest/TransferRequest/RegularizeRequest/PreCreateRequest/PageResult 等 |
| com.hris.employee.util.MaskUtil | Util | 证件号/手机号脱敏 |
| com.hris.employee.util.CodeUtil / JsonUtil | Util | 编码/JSON |

### 2.3 hris-recruit（60 个文件）

| 全限定类名 | 分层 | 一句话职责 |
| --- | --- | --- |
| com.hris.recruit.RecruitApplication | 启动类 | 招聘域入口 |
| com.hris.recruit.client.EmployeeClient | Feign Client | 调用员工域 `/internal/emp/pre-create` 预建档 |
| com.hris.recruit.config.RecruitFeignConfig | 配置 | Feign 配置 |
| com.hris.recruit.controller.RequisitionController | Controller | 招聘需求端点 |
| com.hris.recruit.controller.JobOpeningController | Controller | 职位发布端点 |
| com.hris.recruit.controller.CandidateController | Controller | 候选人主档端点 |
| com.hris.recruit.controller.ApplicationController | Controller | 投递记录端点 |
| com.hris.recruit.controller.InterviewController | Controller | 面试安排端点 |
| com.hris.recruit.controller.InterviewEvalController | Controller | 面试评价端点 |
| com.hris.recruit.controller.OfferController | Controller | Offer 端点 |
| com.hris.recruit.controller.OfferOnboardController | Controller | Offer 录用→员工建档跨域端点 |
| com.hris.recruit.controller.ResumeController | Controller | 简历端点 |
| com.hris.recruit.controller.ChannelController | Controller | 招聘渠道端点 |
| com.hris.recruit.controller.StageLogController | Controller | 阶段流转日志端点 |
| com.hris.recruit.controller.TalentPoolTagController | Controller | 人才库标签端点 |
| com.hris.recruit.service.RequisitionService | Service | 招聘需求单 CRUD |
| com.hris.recruit.service.JobOpeningService | Service | 职位发布 CRUD |
| com.hris.recruit.service.CandidateService | Service | 候选人主档 CRUD（密文+掩码） |
| com.hris.recruit.service.ApplicationService | Service | 投递记录 CRUD（stage/status） |
| com.hris.recruit.service.InterviewService | Service | 面试安排 CRUD |
| com.hris.recruit.service.InterviewEvalService | Service | 面试评价 CRUD |
| com.hris.recruit.service.OfferService | Service | Offer CRUD（薪酬带宽校验、薪酬密文/掩码） |
| com.hris.recruit.service.OfferOnboardService | Service | 录用建档：跨域调员工预建档，幂等标记 offer/candidate 状态 |
| com.hris.recruit.service.ResumeService | Service | 简历附件与解析结果 CRUD |
| com.hris.recruit.service.ChannelService | Service | 渠道字典 CRUD |
| com.hris.recruit.service.StageLogService | Service | 阶段流转日志 |
| com.hris.recruit.service.TalentPoolTagService | Service | 人才库标签 CRUD |
| com.hris.recruit.entity.Requisition | Entity | 招聘需求单 |
| com.hris.recruit.entity.JobOpening | Entity | 职位发布 |
| com.hris.recruit.entity.Candidate | Entity | 候选人主档 |
| com.hris.recruit.entity.Application | Entity | 投递记录 |
| com.hris.recruit.entity.Interview | Entity | 面试安排 |
| com.hris.recruit.entity.InterviewEval | Entity | 面试评价 |
| com.hris.recruit.entity.Offer | Entity | 录用意向 |
| com.hris.recruit.entity.Resume | Entity | 简历 |
| com.hris.recruit.entity.Channel | Entity | 招聘渠道 |
| com.hris.recruit.entity.StageLog | Entity | 阶段流转日志 |
| com.hris.recruit.entity.TalentPoolTag | Entity | 人才库标签 |
| com.hris.recruit.mapper.*（11 个） | Mapper | 上述实体对应 Mapper |
| com.hris.recruit.dto.*（12 个） | DTO | 各实体 Create/Update/Response record |

---

## 3. 核心业务流程

### 3.1 组织主数据维护（hris-org）

```
法人主体（OrgLegalEntity）
   └─ 部门树（OrgDept，物化路径 path = 父.path + "." + id，深度 ≤ 8）
        └─ 部门负责人 managerEmpId（逻辑外键 emp_employee.id）
   岗位（OrgPosition）──┐
   职务（OrgJob）        ├─ 组合刻画"职位"，通过雇佣关系挂到员工
   职级带（OrgGradeBand）┘
        └─ 职级（OrgGrade，可挂职级带）
编制（OrgHeadcount）= 部门 × 岗位 × 季度(yyyyQn) → quota / occupied
```

- **新建部门**：`DeptService.create` 校验父部门存在与树深度（≤8 层），编码缺省按 `d` 前缀生成并校验唯一，雪花 ID 拼物化路径。
- **更新部门**：`DeptService.update` 先写 `org_dept_history` 快照，再按需更新；父部门变更时重算物化路径；部门编码作为版本身份标识**不允许修改**。
- **组织调整申请**：`DeptService.adjust` 落历史快照（bizKey=`ORG_ADJUST:{snowflake}`）并调用 FlowClient 发起流程，返回 bizKey + 流程实例 ID；MVP 下流程调用降级不阻断落单。
- **编制校验**：`HeadcountService.check` 按当前季度计算 `shortfall = count - (quota - occupied)`，供员工域入职/调动前置调用（`/internal/org/hc/check`）。
- **汇报链解析**：`ReportLineService.resolve` 先取员工直属上级，再沿部门树逐级向上收集部门负责人，用 visited 防环。

> **部门调整对员工的影响**：部门本身变更不直接改写员工主档；员工归属通过 `Employment.deptId/positionId` 体现。部门调整生效后，员工花名册按当前雇佣关系实时关联；部门历史快照用于回溯"某时点该员工在哪个部门"。

### 3.2 员工生命周期状态机（hris-employee）

**八态状态机**（`EmployeeStatus`，持久化 `emp_employee.status` TINYINT）：

```
PENDING(0 待入职) ──→ PROBATION(1 试用) ──→ ACTIVE(2 在职) ──→ RESIGNING(3 离职中) ──→ RESIGNED(4 已离职)[终态]
     │                    │                      │
     └→ CANCELLED(5 已取消)[终态]               ├→ RETIRED(6 退休)[终态]
                                               └→ BLACKLISTED(7 黑名单)[终态]
```

合法流转由 `EmployeeState.canTransitionTo` 定义，所有流转统一经 `EmployeeStateMachine.transition` 校验并落 `StatusLog`。

**关键入口**：

| 环节 | 入口 Service | 前置校验 | 动作 |
| --- | --- | --- | --- |
| 手动建档 | `EmployeeService.create` | — | 生成工号、落密文+掩码列、状态=PENDING、初始化四类子档案 |
| 入职办理 | `OnboardingService.create` | 员工必须为 PENDING；组织域编制校验 | 创建 EMP_ONBOARD 生命周期单，载荷含法人/部门/岗位/职级/直属上级/初始账号 |
| 转正 | `RegularizationService.create` | 员工必须为 PROBATION | 创建 EMP_REGULAR 单，生效后 PROBATION→ACTIVE |
| 调动 | `TransferService.create` | 员工必须为 ACTIVE；目标部门/岗位编制校验 | 创建 EMP_TRANSFER 单，生效日经 PendingTransaction 落账，主状态不变 |
| 离职 | `ResignationService.create` | 员工为 ACTIVE 或 PROBATION | 创建 EMP_RESIGN 单（最后工作日为生效日），流程通过后 RESIGNING，到期 RESIGNED |
| 黑名单 | `EmployeeService.blacklist` | 未在黑名单 | 经状态机置 BLACKLISTED 终态 |

> **敏感字段**：证件号/手机号采用"密文列 + 掩码列"双列设计（`certNoCipher/certNoMask`、`mobileCipher/mobileMask`）。MVP 阶段密文列暂存明文（待接 ShardingSphere 加密插件），掩码列始终存脱敏值供列表展示；变更时经 `ChangeAudit` 落 diff 审计。

### 3.3 招聘流程（hris-recruit）

```
Requisition(招聘需求) ──批准──→ JobOpening(职位发布)
                                      │
候选人(Candidate) ──投递──→ Application(投递，含 stage/status)
                                      │
                                      ├─→ Interview(面试安排) ─→ InterviewEval(面试评价)
                                      │
                                      └─→ Offer(录用意向，含薪酬带宽校验)
                                                │
                                                ▼ acceptAndOnboard(offerId)
                              跨域调用 employee /internal/emp/pre-create
                                                │
                              ┌─────────────────┴─────────────────┐
                              ▼                                   ▼
                    员工主档(状态=PENDING)              offer=ACCEPTED, candidate=HIRED
                              │
                              └─→ 后续由 hris-employee 入职办理(EMP_ONBOARD)补全部门/岗位/正式入职
```

- **招聘需求**：`RequisitionService` 维护需求单（部门/岗位/人数/原因/状态），是流程起点。
- **职位发布**：`JobOpeningService` 由需求单批准后对外发布。
- **候选人/投递**：候选人主档（人才库）与投递记录解耦；一个候选人可对多个职位发布产生多条 Application，每条 Application 承载招聘阶段 stage。
- **面试**：`InterviewService` 为某投递安排一轮面试（轮次、计划时间、面试官），`InterviewEvalService` 记录评价。
- **Offer**：`OfferService` 发出录用意向，含薪酬带宽校验结果与薪酬密文/掩码。
- **录用建档联动**（RCU-F-030，跨域核心）：`OfferOnboardService.acceptAndOnboard`
  1. 校验 Offer 存在且未被录用（幂等：已 ACCEPTED 拒绝重复建档）；
  2. 经 Application 取 candidateId，再取候选人姓名/手机/邮箱；
  3. 调用员工域 `EmployeeClient.preCreate` 创建员工主档（状态=PENDING，empNoPrefix=`RC`）；
  4. 成功后置 offer=ACCEPTED、candidate=HIRED，返回新员工 ID。
  5. 部门/岗位/入职办理由后续 EMP_ONBOARD 流程补全。

---

## 4. 关键类清单（每模块最重要 5–10 个）

### hris-org
1. **DeptService** — 部门树/物化路径/版本快照/组织调整申请核心。
2. **HeadcountService** — 编制计划、占用看板与编制校验（员工域入职/调动前置依赖）。
3. **ReportLineService** — 汇报链解析（权限数据范围、审批上报依赖）。
4. **OrgDept / OrgDeptHistory** — 部门现行表 + 历史快照表。
5. **OrgHeadcount** — 部门×岗位×季度编制三元组实体。
6. **LegalEntityService / PositionService / GradeService** — 组织字典层。

### hris-employee
1. **EmployeeService** — 员工主档建档/花名册/敏感字段审计/黑名单/预建档。
2. **EmployeeStateMachine + EmployeeStatus** — 八态状态机与合法流转、状态流水。
3. **OnboardingService / RegularizationService / TransferService / ResignationService** — 四类生命周期办理。
4. **LifecycleCaseService / PendingTransaction** — 生命周期单落库与待生效落账。
5. **Employee / Employment** — 员工主档（密文+掩码）与雇佣关系。
6. **EmployeeInternalController** — 跨域契约（profile-brief/pre-create/pre-hire-link）。

### hris-recruit
1. **OfferOnboardService** — 录用→员工建档跨域联动（幂等）。
2. **RequisitionService / JobOpeningService** — 招聘需求与职位发布。
3. **CandidateService / ApplicationService** — 候选人主档与投递记录。
4. **InterviewService / InterviewEvalService** — 面试安排与评价。
5. **OfferService** — 录用意向与薪酬带宽校验。
6. **OfferOnboardController** — 跨域端点（rcu:offer:onboard）。

---

## 5. 实体关系

```
OrgLegalEntity (法人)
   1───< OrgDept (部门，物化路径自关联 parentId)
OrgPosition (岗位) ──┐
OrgJob (职务)        ├──> OrgHeadcount (部门×岗位×季度，quota/occupied)
OrgGradeBand (职级带) ─┴─< OrgGrade (职级)

Employee (员工主档，status 八态)
   1───< Employment (雇佣关系：法人/部门/岗位/职级/直属上级/生效日，versioned)
   1───< Contract (合同台账)
   1───< StatusLog (状态流水)
   1───< ChangeAudit (敏感字段 diff)
   1───< ProfileEducation/Work/Family/Emergency (子档案)
   1───< LifecycleCase (生命周期单) ──> PendingTransaction (待生效)

Candidate (招聘域候选人)
   1───< Resume (简历)
   1───< TalentPoolTag (标签)
   1───< Application (投递，挂 JobOpening)
             1───< Interview ──< InterviewEval
             1───< Offer (薪酬带宽校验、薪酬密文/掩码)
Requisition ──< JobOpening
Channel ──< Application (渠道归因)
StageLog (阶段流转日志)

跨域关联（逻辑外键，无物理外键）：
  recruit.Candidate ──pre-create──> employee.Employee (PENDING)
  employee.Employment.deptId  ──> org.OrgDept.id
  employee.Employment.positionId ──> org.OrgPosition.id
  employee.Employment.gradeId ──> org.OrgGrade.id
  org.Headcount.occupied <── employee 入职/离职回写
```

**核心字段说明**：
- `tenantId`：所有业务表继承 `BaseEntity`，多租户隔离由 MyBatis-Plus 租户插件自动拼接。
- `Employee.certNoCipher/certNoMask`、`mobileCipher/mobileMask`：密文列（MVP 明文占位）+ 掩码列（脱敏展示）。
- `Offer.salaryAmountCipher/salaryAmountMask`：薪酬密文 + 掩码（同敏感字段策略）。
- `OrgDept.path`：物化路径（如 `1.12.130`），用于树查询与祖先检索。
- `OrgHeadcount(quota, occupied)`：编制额度与已占用，`occupied` 由员工入职流程回写。

---

## 6. 编译验证结果

> 编译环境：JDK 25（`D:\Program Files\Java\jdk-25.0.4.1`，项目 `java.version=21`，JDK 25 向后兼容 release 21）。
> 注意：系统默认 `JAVA_HOME` 指向 JDK 17，直接编译会报"不支持发行版本 21"，需临时切换 JAVA_HOME。

| 模块 | 命令 | 结果 |
| --- | --- | --- |
| hris-org | `mvn -pl hris-org -am compile -DskipTests -q` | **BUILD SUCCESS**（exit 0） |
| hris-employee | `mvn -pl hris-employee -am compile -DskipTests -q` | **BUILD SUCCESS**（exit 0） |
| hris-recruit | `mvn -pl hris-recruit -am compile -DskipTests -q` | **BUILD SUCCESS**（exit 0） |

三个模块在补充注释后均编译通过，未引入语法错误。

---

## 7. 本次注释改动统计

- **改动文件数：47**（仅新增 Javadoc，未改业务代码）
  - hris-org：16（7 Controller + 9 Service）
  - hris-employee：8（4 Controller + 4 Service）
  - hris-recruit：23（12 Controller + 11 Service）
- **未改动文件：131**（Mapper / Entity / DTO / Enum / Config / Client / Util，本已有完善类级注释）
- 全部 178 个主代码文件类级注释齐全，核心 Controller 端点与 Service 公共方法方法级注释齐全。
