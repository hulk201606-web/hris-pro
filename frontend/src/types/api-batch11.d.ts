/**
 * 排期批 9-12 第二轮前端契约（自助/考勤/平台/组织域，字段逐项对照后端 DTO 核实）。
 * 来源：hris-employee SelfDtos、hris-attendance ClosingDtos/LocationDtos/DeviceDtos/PunchDtos、
 *       hris-platform ReportSubscriptionDtos、hris-org OrgVersionDtos。
 */

/* ============================ 自助（emp/me） ============================ */

/** 我的信息（GET /emp/me/profile，证件与手机仅掩码） */
export interface SelfProfile {
  empId: number;
  empNo: string;
  name: string;
  certNoMask: string | null;
  mobileMask: string | null;
  email: string | null;
  address: string | null;
  emergencyContact: string | null;
  emergencyPhone: string | null;
  deptId: number | null;
  positionId: number | null;
  hireType: string | null;
}

/** 非关键字段直改（PATCH /emp/me/profile）；空值表示不修改 */
export interface SelfProfileUpdateRequest {
  name?: string;
  mobile?: string;
  email?: string;
  address?: string;
  emergencyContact?: string;
  emergencyPhone?: string;
  certNo?: string;       // 关键字段：仅用于触发后端拒绝检测，业务侧不发送
  certType?: string;     // 同上
  hireType?: string;     // 同上
  bankAccount?: string;  // 同上
}

/** 关键字段变更申请（POST /emp/me/profile/change-requests），至少一项 */
export interface CriticalChangeRequest {
  certNo?: string;
  certType?: string;
  bankAccount?: string;
  hireType?: string;
  reason?: string;
}

/** 变更申请响应：bizKey 可用于跟踪审批进度 */
export interface CriticalChangeResponse {
  bizKey: string;
  templateKey: string;
  procInstId: string | null;
  status: string;
}

/** 自助首页-个人资料子项 */
export interface SelfProfileSection {
  name: string | null;
  empNo: string | null;
  deptName: string | null;
  positionName: string | null;
  hireDate: string | null;
  source: string;
}

/** 自助首页-今日考勤子项（todayStatus = NO_RECORD 表示无记录） */
export interface SelfAttendanceSection {
  todayStatus: string | null;
  monthLateCount: number | null;
  source: string;
}

/** 自助首页-假期余额子项 */
export interface SelfLeaveSection {
  items: { leaveType: string | null; remainingDays: string }[];
  source: string;
}

/** 自助首页-最新工资条子项（source=fallback 表示无权或下游不可用） */
export interface SelfPayslipSection {
  latestPeriod: string | null;
  netAmountMasked: string | null;
  source: string;
}

/** 自助首页-待办计数子项 */
export interface SelfPendingFlowSection {
  count: number | null;
  source: string;
}

/** 自助首页聚合（GET /emp/me/dashboard）；partial=true 表示至少一个子项降级 */
export interface SelfDashboard {
  profile: SelfProfileSection;
  attendance: SelfAttendanceSection;
  leaves: SelfLeaveSection;
  payslip: SelfPayslipSection;
  pendingFlows: SelfPendingFlowSection;
  partial: boolean;
  costMs: number;
}

/* ============================ 考勤（att） ============================ */

/** 当日考勤（GET /att/daily/today）：punchStatus 如 NORMAL/LATE/ABSENT */
export interface AttPunchToday {
  empId: number;
  workDate: string | null;
  shiftName: string | null;
  punchIn: string | null;
  punchOut: string | null;
  punchStatus: string | null;
}

/** 假勤台账流水（GET /att/ledgers 分页行） */
export interface AttLedgerRow {
  id: number;
  empId: number;
  leaveTypeId: number;
  leaveTypeName: string | null;
  changeAmount: number;
  bizRef: string | null;
  createdAt: string;
}

/** 日考勤月结锁（GET /att/daily-close，status = ACTIVE/RELEASED） */
export interface DailyCloseLock {
  id: number;
  period: string;
  status: string;
  closedBy: number | null;
  closedAt: string | null;
  releasedBy: number | null;
  releasedAt: string | null;
  releaseReason: string | null;
  createdAt: string;
  updatedAt: string;
}

/** 考勤围栏（GET /att/locations） */
export interface AttLocation {
  id: number;
  name: string;
  lat: number;
  lng: number;
  radiusM: number | null;
  enabled: boolean | null;
  createdAt: string;
  updatedAt: string;
}

/** 考勤机设备（GET /att/devices，响应不含密钥） */
export interface AttDevice {
  id: number;
  deviceSn: string;
  locationId: number | null;
  status: string | null;
  createdAt: string;
  updatedAt: string;
}

/* ============================ 平台（plt） ============================ */

/** 报表订阅（GET /plt/reports/subscriptions；status 1=启用 0=停用） */
export interface ReportSubscription {
  id: number;
  reportDefId: number;
  cronExpr: string;
  channels: string[];
  recipients: string[];
  status: number;
  schedulerTaskKey: string | null;
  scheduled: boolean;
  updatedAt: string;
}

/** 订阅创建/更新请求（channels 取 EMAIL/WECOM/DINGTALK） */
export interface ReportSubscriptionRequest {
  reportDefId: number;
  cronExpr: string;
  channels: string[];
  recipients: string[];
  status: number;
}

/* ============================ 组织（org） ============================ */

/** 组织版本摘要（GET /org/versions；status = DRAFT/ACTIVE/SUPERSEDED） */
export interface OrgVersion {
  versionNo: string;
  snapshotDate: string;
  positionCount: number;
  gradeCount: number;
  status: string;
  effectiveFrom: string | null;
}
