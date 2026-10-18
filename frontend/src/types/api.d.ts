/**
 * 前后端共享接口类型契约（对齐 docs/openapi.yaml）
 * 统一响应体：{ code, message, data, traceId, timestamp }；分页 data = { records, page, size, total }
 */

/** 统一响应信封 */
export interface ApiEnvelope<T = unknown> {
  /** 0=成功，非 0=错误（分段见 docs/07 §2） */
  code: number;
  message: string;
  data: T;
  traceId: string;
  timestamp: number;
}

/** 分页响应 */
export interface PageResult<T> {
  records: T[];
  page: number;
  size: number;
  total: number;
}

/* ============================ auth ============================ */

export interface LoginRequest {
  tenantCode: string;
  username: string;
  password: string;
}

export interface RefreshRequest {
  refreshToken: string;
}

export interface TokenResponse {
  accessToken: string;
  refreshToken: string;
  tokenType: string;
  /** access 有效期（秒） */
  expiresIn: number;
  /** refresh 有效期（秒） */
  refreshExpiresIn: number;
}

export interface UserInfo {
  userId: number;
  employeeId: number;
  username: string;
  /** 1=正常 0=禁用 */
  status: number;
}

export interface RoleInfo {
  code: string;
  name: string;
}

export interface DataScopeInfo {
  roleCode: string;
  /** SELF / DEPT / DEPT_SUB / CUSTOM / ALL */
  scopeType: string;
  /** CUSTOM 时的部门 ID 集合（JSON 数组字符串） */
  deptIds: string;
}

export interface TenantBrand {
  tenantId: number;
  code: string;
  name: string;
  plan: string;
  timezone: string;
  domain: string;
}

export interface MeResponse {
  user: UserInfo;
  roles: RoleInfo[];
  /** 权限点（模块:资源:动作） */
  perms: string[];
  dataScopes: DataScopeInfo[];
  tenant: TenantBrand;
}

export interface MenuNode {
  code: string;
  name: string;
  /** module / menu / button */
  type: string;
  children?: MenuNode[];
}

/* ============================ employee ============================ */

export interface EmploymentInfo {
  legalEntityId: number;
  deptId: number;
  positionId: number;
  gradeId: number;
  hireType: string;
  supervisorEmpId: number;
  effectiveFrom: string;
}

export interface ProfileEducationDto {
  id: number;
  school: string;
  major: string;
  degree: string;
  startDate: string;
  endDate: string;
}

export interface ProfileWorkDto {
  id: number;
  company: string;
  positionName: string;
  startDate: string;
  endDate: string;
  description: string;
}

export interface ProfileFamilyDto {
  id: number;
  name: string;
  relation: string;
  phone: string;
  company: string;
}

export interface ProfileEmergencyDto {
  id: number;
  name: string;
  relation: string;
  phone: string;
  address: string;
}

export interface EmployeeProfileResponse {
  id: number;
  empNo: string;
  name: string;
  gender: string;
  birthDate: string;
  email: string;
  /** 在职状态码 */
  status: number;
  statusLabel: string;
  blacklisted: number;
  /** 脱敏证件号（默认返回） */
  certNoMask: string;
  /** 脱敏手机号（默认返回） */
  mobileMask: string;
  /** 明文证件号（仅密域角色回填） */
  certNo?: string;
  /** 明文手机号（仅密域角色回填） */
  mobile?: string;
  employment?: EmploymentInfo;
  educations?: ProfileEducationDto[];
  works?: ProfileWorkDto[];
  families?: ProfileFamilyDto[];
  emergencies?: ProfileEmergencyDto[];
}

/* ============================ flow ============================ */

export interface TaskView {
  taskId: string | null;
  instanceId: string;
  procDefKey: string;
  bizType: string;
  bizKey: string;
  nodeName: string;
  assignee: string;
  /** TODO / DONE / RUNNING / FINISHED */
  status: string;
  startTime: string;
  createTime: string;
  endTime: string;
  durationInMillis: number;
}

export interface TaskActionRequest {
  comment?: string;
  attachments?: number[];
  targetUserId?: number;
}

/* ============================ 审批流（flow + attendance 请求单） ============================ */

/** 流程发起请求 */
export interface StartInstanceRequest {
  bizType: string;
  bizKey: string;
  procDefKey?: string;
  payload?: Record<string, unknown>;
  variables?: Record<string, unknown>;
  initiator?: number;
  assignee?: number;
}

export interface StartInstanceResponse {
  instanceId: string;
  bizType: string;
  bizKey: string;
  status: string;
}

/** 请假单 */
export interface LeaveRequest {
  id: number;
  empId: number;
  leaveTypeId: number;
  leaveTypeName: string;
  bizKey: string;
  startTime: string;
  endTime: string;
  days: number;
  reason: string;
  status: string;
  procInstId: string;
  createdAt: string;
  updatedAt: string;
}

export interface LeaveCreateRequest {
  empId: number;
  leaveTypeId: number;
  startTime: string;
  endTime: string;
  reason?: string;
}

/** 加班单 */
export interface OvertimeRequest {
  id: number;
  empId: number;
  bizKey: string;
  startTime: string;
  endTime: string;
  hours: number;
  reason: string;
  mode: string;
  status: string;
  procInstId: string;
  dayType: string;
  rate: number;
  createdAt: string;
  updatedAt: string;
}

export interface OvertimeCreateRequest {
  empId: number;
  startTime: string;
  endTime: string;
  reason?: string;
  mode?: string;
}

/** 补卡单 */
export interface FixRequest {
  id: number;
  empId: number;
  bizKey: string;
  workDate: string;
  fixTime: string;
  type: string;
  reason: string;
  status: string;
  procInstId: string;
  createdAt: string;
  updatedAt: string;
}

export interface FixCreateRequest {
  empId: number;
  workDate: string;
  fixTime: string;
  type: string;
  reason?: string;
}

/** 考勤请求单查询参数 */
export interface AttRequestQuery {
  empId?: number;
  status?: string;
  from?: string;
  to?: string;
}

/* ============================ self（自助聚合，本阶段占位） ============================ */

/** 自助首页摘要（SELF-F-001）：待办数 + 假期余额 */
export interface SelfHomeSummary {
  /** 我的待办数量 */
  todoCount: number;
  /** 剩余年假天数（假期额度占位，二期对接 att/quotas） */
  leaveBalanceDays: number;
  /** 在职状态文案 */
  statusLabel: string;
}
