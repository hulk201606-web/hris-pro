# Shard-C 业务梳理：考勤 / 薪酬 / 绩效三域

> 范围：`hris-attendance`（89 文件）、`hris-compensation`（71 文件）、`hris-performance`（85 文件），共 245 个主代码文件。
> 系统：智云 HR 多租户 SaaS。本文档基于代码阅读与注释补全过程梳理。

---

## 1. 模块总览

| 模块 | 业务域 | 核心职责 | 分层结构 |
|------|--------|----------|----------|
| hris-attendance | 考勤 | 排班、打卡、日结、请假/加班/补卡/出差审批、异常处理、月结锁账 | controller / service / entity / mapper / dto / client / config / util |
| hris-compensation | 薪酬 | 薪资档案、薪资组/薪级/薪资项、算薪批次、算薪预览/明细、工资单、发放指令、社保公积金、个税扣除、密域脱敏 | controller / service / entity / mapper / dto / client / config |
| hris-performance | 绩效 | 绩效计划/周期、目标/OKR、评估任务、自评/上级评、校准会话、强制分布、申诉、培训计划/课程/报名/学分 | controller / service / entity / mapper / dto / client / config |

---

## 2. 包结构表

### 2.1 hris-attendance（考勤域）

| 全限定类名 | 分层 | 一句话职责 |
|------------|------|-----------|
| com.hris.attendance.AttendanceApplication | 启动类 | Spring Boot 服务启动入口，扫描 com.hris 根包 |
| com.hris.attendance.client.FlowClient | Feign Client | 调用工作流引擎（hris-flow）发起审批流 |
| com.hris.attendance.config.AttendanceFeignConfig | 配置 | Feign 拦截器，透传租户/鉴权头 |
| com.hris.attendance.controller.*Controller | Controller | REST 端点，挂 @RequirePerm 权限点 |
| com.hris.attendance.service.AttendanceCalculator | Service/规则 | 日考勤状态判定与异常生成规则（迟到/早退/旷工/缺卡） |
| com.hris.attendance.service.DailyAttendanceService | Service | 日考勤计算编排，幂等 upsert |
| com.hris.attendance.service.MonthlyClosingService | Service | 月结锁账，状态机 INIT→RUNNING→DONE/FAILED |
| com.hris.attendance.service.LeaveRequestService | Service | 请假申请管理与审批联动 |
| com.hris.attendance.service.LeaveQuotaService | Service | 假期额度发放/消耗/结转 |
| com.hris.attendance.service.LeaveQueryService | Service | 日结时查询员工当日已批假时长 |
| com.hris.attendance.service.OvertimeService | Service | 加班申请与日结时累计加班时长 |
| com.hris.attendance.service.FixRequestService | Service | 补卡申请管理 |
| com.hris.attendance.service.TripService | Service | 出差申请管理 |
| com.hris.attendance.service.ExceptionService | Service | 考勤异常单处理（解决/忽略） |
| com.hris.attendance.service.PunchService | Service | 打卡记录写入与查询 |
| com.hris.attendance.service.ShiftService | Service | 班次定义 CRUD |
| com.hris.attendance.service.ShiftAssignmentService | Service | 排班分配（批量/周期） |
| com.hris.attendance.service.GroupService | Service | 考勤组管理 |
| com.hris.attendance.service.PayrollCycleService | Service | 薪资核算周期管理 |
| com.hris.attendance.service.ReportService | Service | 考勤报表（假期消耗统计） |
| com.hris.attendance.service.ApprovalService | Service | 审批回调处理 |
| com.hris.attendance.service.FlowIntegration | Service | 与工作流引擎的集成 |
| com.hris.attendance.entity.* | Entity | MyBatis-Plus 实体 DO |
| com.hris.attendance.mapper.* | Mapper | MyBatis-Plus Mapper 接口 |
| com.hris.attendance.dto.* | DTO | 请求/响应数据结构 |
| com.hris.attendance.util.CodeUtil | Util | 业务单据编号生成 |
| com.hris.attendance.util.TimeAmountUtil | Util | BigDecimal 时长归一化 |

### 2.2 hris-compensation（薪酬域）

| 全限定类名 | 分层 | 一句话职责 |
|------------|------|-----------|
| com.hris.compensation.CompensationApplication | 启动类 | Spring Boot 服务启动入口 |
| com.hris.compensation.client.AttendanceClient | Feign Client | 调用考勤域取月结汇总与日明细 |
| com.hris.compensation.client.EmployeeClient | Feign Client | 调用员工域取员工主档简报 |
| com.hris.compensation.service.PayrollRunService | Service | 算薪批次 CRUD（状态机 DRAFT→CALCULATING→LOCKED→PAID→ARCHIVED） |
| com.hris.compensation.service.PayrollPreviewService | Service | 算薪预览：组合员工主档+考勤结果 |
| com.hris.compensation.service.SalarySecretService | Service | 密域读取：校验 cmp:salary:read 后返回密文+掩码 |
| com.hris.compensation.service.EmployeePayService | Service | 员工薪资档案（定薪记录）CRUD |
| com.hris.compensation.service.PayGradeService | Service | 薪级 CRUD |
| com.hris.compensation.service.PayGroupService | Service | 薪资组 CRUD |
| com.hris.compensation.service.PayItemService | Service | 薪资项目（工资条项）CRUD |
| com.hris.compensation.service.PayrollDetailService | Service | 算薪明细 CRUD |
| com.hris.compensation.service.PayslipService | Service | 工资单 CRUD/发送 |
| com.hris.compensation.service.PaymentInstructionService | Service | 银行发放指令 CRUD |
| com.hris.compensation.service.PayrollAdjustmentService | Service | 算薪调整项 CRUD |
| com.hris.compensation.service.SocialFundProfileService | Service | 社保公积金档案 CRUD |
| com.hris.compensation.service.TaxDeductionService | Service | 个税专项附加扣除 CRUD |
| com.hris.compensation.service.ImportBatchService | Service | 批量导入批次 CRUD |
| com.hris.compensation.entity.* | Entity | 薪酬实体 DO（含密域字段 cipher/mask 双列） |
| com.hris.compensation.mapper.* | Mapper | MyBatis-Plus Mapper 接口 |
| com.hris.compensation.dto.* | DTO | 请求/响应数据结构 |

### 2.3 hris-performance（绩效域）

| 全限定类名 | 分层 | 一句话职责 |
|------------|------|-----------|
| com.hris.performance.PerformanceApplication | 启动类 | Spring Boot 服务启动入口 |
| com.hris.performance.client.EmployeeClient | Feign Client | 调用员工域取员工信息 |
| com.hris.performance.service.CycleService | Service | 绩效周期 CRUD（状态机 NOT_STARTED→IN_PROGRESS→SELF_REVIEW→MANAGER_REVIEW→CALIBRATION→ARCHIVED） |
| com.hris.performance.service.PlanService | Service | 绩效计划模板 CRUD |
| com.hris.performance.service.GoalService | Service | 绩效目标 CRUD |
| com.hris.performance.service.OkrObjectiveService | Service | OKR 目标 CRUD |
| com.hris.performance.service.EvalTaskService | Service | 评估任务指派/提交 |
| com.hris.performance.service.EvaluationService | Service | 评估结果（评分/评语）CRUD |
| com.hris.performance.service.CalibrationSessionService | Service | 校准会话管理 |
| com.hris.performance.service.CalibrationSnapshotService | Service | 校准快照回溯 |
| com.hris.performance.service.ForcedRuleService | Service | 强制分布规则配置 |
| com.hris.performance.service.AppealService | Service | 绩效申诉处理 |
| com.hris.performance.service.TrainPlanService | Service | 培训计划 CRUD |
| com.hris.performance.service.CourseService | Service | 课程 CRUD |
| com.hris.performance.service.ClassSessionService | Service | 培训班次 CRUD |
| com.hris.performance.service.EnrollmentService | Service | 报名/退报/完成 |
| com.hris.performance.service.InstructorService | Service | 讲师主档 CRUD |
| com.hris.performance.service.CreditLedgerService | Service | 学分台账累计 |
| com.hris.performance.service.ReviewContextService | Service | 评审上下文聚合 |
| com.hris.performance.entity.* | Entity | 绩效实体 DO |
| com.hris.performance.mapper.* | Mapper | MyBatis-Plus Mapper 接口 |
| com.hris.performance.dto.* | DTO | 请求/响应数据结构 |

---

## 3. 核心业务流程

### 3.1 考勤域流程

```
班次定义(Shift) → 排班分配(ShiftAssignment) → 打卡采集(PunchRecord)
                                                      ↓
请假/加班/补卡/出差审批(LeaveRequest/OvertimeRequest/FixRequest/TripRequest)
                                                      ↓
                              日结计算(DailyAttendanceService.compute)
                              规则: AttendanceCalculator.resolveStatus
                              优先级: 法定节假日 > 假期 > 排班 > 默认
                                                      ↓
                              异常单生成(AttendanceException)
                              状态机: OPEN → RESOLVED / IGNORED
                                                      ↓
                              月结锁账(MonthlyClosingService.run)
                              状态机: INIT → RUNNING → DONE / FAILED
                              前置: 周期内未决异常单必须为 0
                              锁账后: 历史不可改, 差异走 ClosingAdjustment 红冲下期
```

**关键状态机：**
- 日考勤状态：NORMAL / LATE / EARLY / ABSENT / LEAVE
- 异常单状态：OPEN（待处理）→ RESOLVED（已解决）/ IGNORED（已忽略）
- 月结批次：INIT → RUNNING → DONE / FAILED
- 审批单（请假/加班/补卡/出差）：DRAFT → APPROVING → APPROVED / REJECTED

**业务校验：**
- 日结为幂等 upsert（uk(tenant_id, emp_id, work_date)）
- 迟到宽限 15 分钟，超 60 分钟按旷工半天预警
- 月结锁账前未决异常必须清零，否则 FAILED
- 已 DONE 周期不可重复锁账（幂等保护）

### 3.2 薪酬域流程

```
薪资档案维护(EmployeePay: 定薪金额+银行账号, 密文存储)
        ↓
薪资组(PayGroup) + 薪级(PayGrade) + 薪资项(PayItem)
        ↓
算薪批次创建(PayrollRun, 状态=DRAFT)
        ↓
算薪预览(PayrollPreviewService.preview)
  ├─ Feign 调用考勤域: 月结汇总 + 个人日明细(加班时长/请假时长)
  └─ Feign 调用员工域: 员工主档简报(empNo/name/deptId)
        ↓
算薪执行(PayrollRun → CALCULATING)
  ├─ 引用考勤月结结果(请假/加班时长)
  ├─ 引用薪资档案(基本工资)
  ├─ 引用社保公积金(SocialFundProfile)
  └─ 引用个税扣除(TaxDeduction)
        ↓
算薪明细生成(PayrollDetail, amountCipher/netAmountCipher 密文)
        ↓
审批(PayrollRun → LOCKED, 锁单后不可改)
        ↓
工资单生成(Payslip, netPayCipher 实发明文)
        ↓
发放指令(PaymentInstruction → PAID)
        ↓
归档(PayrollRun → ARCHIVED)
```

**算薪批次状态机：** DRAFT → CALCULATING → LOCKED → PAID → ARCHIVED

**密域脱敏规则：**
| 实体 | 密文字段 | 掩码字段 | 脱敏规则 |
|------|----------|----------|----------|
| EmployeePay | salaryAmountCipher, bankAccountCipher | salaryAmountMask, bankAccountMask | 非授权视图仅返回 mask |
| PayrollDetail | amountCipher, netAmountCipher | amountMask, netAmountMask | 同上 |
| Payslip | netPayCipher | netPayMask | 同上 |
| TaxDeduction | deductionAmountCipher | deductionAmountMask | 同上 |

密域读取统一走 `SalarySecretService`，校验权限点 `cmp:salary:read`，缺失抛 `CMP_SALARY_FORBIDDEN(54001)`。

### 3.3 绩效域流程

```
绩效计划(Plan, 模板) → 绩效周期创建(Cycle, 状态=NOT_STARTED)
        ↓
周期启动(Cycle → IN_PROGRESS)
        ↓
目标设定(Goal: 标题/权重/目标值) 或 OKR(OkrObjective: O+KR列表)
        ↓
过程跟踪
        ↓
自评阶段(Cycle → SELF_REVIEW): 员工提交自评评分
        ↓
上级评阶段(Cycle → MANAGER_REVIEW): 上级评分+评语
        ↓
校准会议(Cycle → CALIBRATION): 部门级校准会话
  ├─ 校准快照(CalibrationSnapshot): 会前/会后评分 JSON
  └─ 强制分布(ForcedRule): S/A/B/C 比例拉齐
        ↓
评级归档(Cycle → ARCHIVED)
        ↓
绩效申诉(Appeal): OPEN → PROCESSING → RESOLVED/REJECTED
```

**绩效周期状态机：** NOT_STARTED → IN_PROGRESS → SELF_REVIEW → MANAGER_REVIEW → CALIBRATION → ARCHIVED

**培训子流程（绩效域内）：**
```
培训计划(TrainPlan) → 课程(Course) → 培训班次(ClassSession)
  → 员工报名(Enrollment: ENROLLED→COMPLETED/WITHDRAWN)
  → 学分入账(CreditLedger)
```

---

## 4. 关键类清单

### 4.1 attendance（考勤）
1. **AttendanceCalculator** — 日考勤状态判定规则引擎（迟到/早退/旷工/缺卡/请假）
2. **DailyAttendanceService** — 日结计算编排，幂等 upsert
3. **MonthlyClosingService** — 月结锁账，状态机 INIT→RUNNING→DONE/FAILED
4. **LeaveRequestService** — 请假申请与审批联动，扣减额度
5. **PunchService** — 打卡原始记录写入
6. **ShiftAssignmentService** — 排班分配
7. **ExceptionService** — 考勤异常单处理
8. **PayrollCycleService** — 薪资核算周期定义（考勤月结与算薪的时间锚点）

### 4.2 compensation（薪酬）
1. **PayrollRunService** — 算薪批次状态机管理
2. **PayrollPreviewService** — 跨域取数组合（员工主档+考勤月结）
3. **SalarySecretService** — 密域读取权限校验与脱敏响应
4. **EmployeePay** — 薪资档案密域实体（定薪+银行账号 cipher/mask 双列）
5. **PayrollDetail** — 算薪明细密域实体
6. **Payslip** — 工资单密域实体
7. **SocialFundProfileService** — 社保公积金档案

### 4.3 performance（绩效）
1. **CycleService** — 绩效周期状态机管理
2. **EvaluationService** — 评估结果提交与汇总
3. **CalibrationSessionService** — 校准会议管理
4. **GoalService** — 绩效目标设定
5. **AppealService** — 绩效申诉处理
6. **EnrollmentService** — 培训报名与学分
7. **ForcedRuleService** — 强制分布规则

---

## 5. 跨模块联动

### 5.1 考勤结果 → 算薪

```
hris-attendance                          hris-compensation
─────────────                            ─────────────────
PayrollCycle (核算周期起止)
MonthlyClosing (月结锁账, DONE)
  ↓ dailyService.findByRange()
DailyAttendance (日考勤结果)
  统计: overtimeHours / leaveHours
  ↓ Feign: AttendanceClient.monthly(periodCode)
           AttendanceClient.daily(empId, from, to)
                                    ↓
                           PayrollPreviewService.preview()
                           → 组合员工主档 + 考勤汇总
                           → 形成算薪输入
```

- 考勤月结 DONE 后，薪酬域通过 Feign 内部端点 `/internal/att/monthly` 和 `/internal/att/daily` 取数
- 取数内容：月结状态、员工数、状态分布（迟到/早退/旷工/请假天数）、加班时长合计、请假时长合计
- 内部端点由网关封禁外部访问，仅服务间 Feign 直连

### 5.2 绩效结果 → 薪酬

- 绩效评级（Cycle.ARCHIVED 后的最终等级）作为薪酬域 `PayItem` 中"绩效奖金"项的计算系数输入
- 当前 MVP 阶段通过手工导入（ImportBatch）或薪资档案调整项（PayrollAdjustment）方式联动，未实现自动 MQ 事件推送
- 后续规划：绩效周期 ARCHIVED 时发 MQ 事件，薪酬域消费后自动更新绩效奖金基数

---

## 6. 编译验证结果

| 模块 | 命令 | 结果 |
|------|------|------|
| hris-attendance | `mvn -pl hris-attendance -am clean compile -DskipTests` | BUILD SUCCESS |
| hris-compensation | `mvn -pl hris-compensation -am clean compile -DskipTests` | BUILD SUCCESS |
| hris-performance | `mvn -pl hris-performance -am clean compile -DskipTests` | BUILD SUCCESS |

> 编译环境：JDK 25（项目要求 release 21，本机默认 JDK 17 不满足，使用 `D:\Program Files\Java\jdk-25.0.4.1` 编译通过）。
> 三个模块均 `clean compile` 通过，注释改动未引入任何编译错误。

---

## 7. 注释补全统计

| 项目 | 数量 |
|------|------|
| 总文件数 | 245 |
| 修改文件数 | 167（类注释替换/扩写 + 方法注释新增） |
| 类注释补全（DTO/Entity） | 80 个文件 |
| 方法注释补全（CRUD Controller/Service） | 87 个文件 |
| 密域方法注释（SalarySecretService） | 4 个方法 |
| 编译状态 | 三模块全绿 |
