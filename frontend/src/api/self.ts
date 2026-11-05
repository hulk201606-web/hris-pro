/**
 * 员工自助域 API 封装（聚合自助门户专属端点）。
 * 覆盖：我的信息（SELF-F-002 直改 + 关键字段申请） / 自助首页聚合（SELF-F-001） /
 *       我的假期（额度余额 / 台账流水） / 我的工资条（掩码列表 / 密域明文） /
 *       今日考勤与假勤台账（批 11）。
 * 端点均按当前登录用户过滤（后端经网关透传身份解析 employeeId），
 * 工资条仅返回掩码字段，明文读取需密域权限并走独立端点。
 */
import { get, patch, post } from './http';
import type {
  AttLedgerRow,
  AttPunchToday,
  CriticalChangeRequest,
  CriticalChangeResponse,
  SelfDashboard,
  SelfProfile,
  SelfProfileUpdateRequest,
} from '../types/api';
import type { PageResult } from '../types/api';

/* ---------------------------- 我的假期（att 自助） ---------------------------- */

/** 假期额度行（GET /att/leave-quotas/me 单项）：availableQuota = totalQuota - usedQuota */
export interface MyLeaveQuota {
  id: number;
  empId: number;
  leaveTypeId: number;
  leaveTypeName: string | null;
  /** 额度周期（4 位年份） */
  period: string;
  totalQuota: number;
  usedQuota: number;
  availableQuota: number;
}

/** 员工余额视图（GET /att/leave-quotas/me 响应体） */
export interface MyQuotaBalance {
  empId: number;
  quotas: MyLeaveQuota[];
}

/** 假期台账流水（GET /att/leave-quotas/me/ledgers 单项） */
export interface MyLeaveLedger {
  id: number;
  empId: number;
  leaveTypeId: number;
  leaveTypeName: string | null;
  /** 变动天数：发放为正、请假扣减为负 */
  changeAmount: number;
  bizRef: string | null;
  createdAt: string;
}

/** 当前员工假期余额自助视图（GET /att/leave-quotas/me） */
export function fetchMyQuotaBalance(): Promise<MyQuotaBalance> {
  return get<MyQuotaBalance>('/att/leave-quotas/me');
}

/** 当前员工假期台账流水（GET /att/leave-quotas/me/ledgers） */
export function fetchMyLeaveLedgers(leaveTypeId?: number): Promise<MyLeaveLedger[]> {
  return get<MyLeaveLedger[]>('/att/leave-quotas/me/ledgers', leaveTypeId ? { leaveTypeId } : undefined);
}

/* ---------------------------- 我的工资条（cmp 自助） ---------------------------- */

/** 员工自助工资条行（GET /cmp/payslips/me 单项）：仅掩码字段，无密文 */
export interface MyPayslip {
  id: number;
  runId: number;
  /** 薪酬周期编码（如 2026-01），取自算薪批次 */
  periodCode: string | null;
  /** 应发金额掩码，取自核算明细 */
  grossMask: string | null;
  /** 实发金额掩码 */
  netPayMask: string | null;
  /** 发放状态 PENDING/SENT/VIEWED */
  sendStatus: string;
  sentAt: string | null;
}

/** 工资条密域读取（GET /cmp/salary-secret/payslips/{id} 响应体，需密域权限） */
export interface PayslipSecret {
  id: number;
  empId: number;
  netPayCipher: string | null;
  netPayMask: string | null;
  updatedAt: string | null;
}

/** 当前员工工资条列表（GET /cmp/payslips/me，按登录用户过滤，仅掩码） */
export function fetchMyPayslips(): Promise<MyPayslip[]> {
  return get<MyPayslip[]>('/cmp/payslips/me');
}

/** 工资条明文实发（GET /cmp/salary-secret/payslips/{id}，需 cmp:payslip:read + cmp:salary:read） */
export function fetchPayslipSecret(id: number): Promise<PayslipSecret> {
  return get<PayslipSecret>(`/cmp/salary-secret/payslips/${id}`);
}

/* ---------------------------- 我的信息（SELF-F-002 / EMP-F-021） ---------------------------- */

/** 我的信息（GET /emp/me/profile，需 emp:employee:read；证件/手机仅掩码） */
export function fetchSelfProfile(): Promise<SelfProfile> {
  return get<SelfProfile>('/emp/me/profile');
}

/**
 * 自助信息直改（PATCH /emp/me/profile，需 emp:employee:write）。
 * 仅姓名/手机/邮箱/地址/紧急联系人可直改；提交关键字段会被后端整体拒绝。
 */
export function updateSelfProfile(data: SelfProfileUpdateRequest): Promise<SelfProfile> {
  return patch<SelfProfile>('/emp/me/profile', data);
}

/**
 * 关键信息变更申请（POST /emp/me/profile/change-requests）。
 * 落 EMP_PROFILE_CHG 审批，返回 bizKey 与 procInstId。
 */
export function requestCriticalChange(data: CriticalChangeRequest): Promise<CriticalChangeResponse> {
  return post<CriticalChangeResponse>('/emp/me/profile/change-requests', data);
}

/* ---------------------------- 自助首页聚合（SELF-F-001） ---------------------------- */

/** 自助首页聚合（GET /emp/me/dashboard，单端点返回 5 个子项与 partial 降级标记） */
export function fetchSelfDashboard(): Promise<SelfDashboard> {
  return get<SelfDashboard>('/emp/me/dashboard');
}

/* ---------------------------- 自助考勤（批 11） ---------------------------- */

/** 当日考勤卡片（GET /att/daily/today?empId=，班次/上下班打卡/状态） */
export function fetchTodayAttendance(empId: number): Promise<AttPunchToday> {
  return get<AttPunchToday>('/att/daily/today', { empId });
}

/** 假勤台账分页（GET /att/ledgers，可按假种与时间区间过滤） */
export function fetchAttendanceLedgers(params: {
  empId?: number;
  leaveTypeId?: number;
  from?: string;
  to?: string;
  page: number;
  size: number;
}): Promise<PageResult<AttLedgerRow>> {
  return get<PageResult<AttLedgerRow>>('/att/ledgers', params);
}
