/**
 * 员工自助域 API 封装（聚合自助门户专属端点）。
 * 覆盖：我的假期（额度余额 / 台账流水）、我的工资条（掩码列表 / 密域明文）。
 * 端点均按当前登录用户过滤（后端经网关透传身份解析 employeeId），
 * 工资条仅返回掩码字段，明文读取需密域权限并走独立端点。
 */
import { get } from './http';

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
