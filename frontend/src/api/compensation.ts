/**
 * 薪酬域 API 封装（后端 hris-compensation 模块）。
 * 覆盖：薪酬核算批次（payroll-runs）CRUD、员工薪酬档（employee-pays）CRUD、
 *       算薪取数预览（跨域组合员工主档 + 考勤月结）。
 */
import { del, get, post, put } from './http';
import { isMockEnabled } from './mock';
import { adminMock } from './admin-mock';
import type { PageResult } from '../types/api';
import type { EmployeePay, PayrollPreviewLine, PayrollRun } from '../types/admin';
import type { PeriodLock } from '../types/api-batch2';

/* ---------------------------- 薪酬核算批次 ---------------------------- */

/** 分页查询薪酬核算批次（GET /cmp/payroll-runs） */
export function listPayrollRuns(page: number, size: number): Promise<PageResult<PayrollRun>> {
  if (isMockEnabled()) return adminMock.listPayrollRuns(page, size);
  return get<PageResult<PayrollRun>>('/cmp/payroll-runs', { page, size });
}

/** 新建薪酬核算批次（POST /cmp/payroll-runs） */
export function createPayrollRun(data: Record<string, unknown>): Promise<PayrollRun> {
  if (isMockEnabled()) return adminMock.createPayrollRun(data as any);
  return post<PayrollRun>('/cmp/payroll-runs', data);
}

/** 更新薪酬核算批次（PUT /cmp/payroll-runs/{id}） */
export function updatePayrollRun(id: number, data: Record<string, unknown>): Promise<PayrollRun> {
  if (isMockEnabled()) return adminMock.updatePayrollRun(id, data as any);
  return put<PayrollRun>(`/cmp/payroll-runs/${id}`, data);
}

/** 删除薪酬核算批次（DELETE /cmp/payroll-runs/{id}） */
export function deletePayrollRun(id: number): Promise<void> {
  if (isMockEnabled()) return adminMock.removePayrollRun(id);
  return del<void>(`/cmp/payroll-runs/${id}`);
}

/* ---------------------------- 员工薪酬档 ---------------------------- */

/** 分页查询员工薪酬档（GET /cmp/employee-pays） */
export function listEmployeePays(page: number, size: number): Promise<PageResult<EmployeePay>> {
  if (isMockEnabled()) return adminMock.listEmployeePays(page, size);
  return get<PageResult<EmployeePay>>('/cmp/employee-pays', { page, size });
}

/** 新建员工薪酬档（POST /cmp/employee-pays） */
export function createEmployeePay(data: Record<string, unknown>): Promise<EmployeePay> {
  if (isMockEnabled()) return adminMock.createEmployeePay(data as any);
  return post<EmployeePay>('/cmp/employee-pays', data);
}

/** 更新员工薪酬档（PUT /cmp/employee-pays/{id}） */
export function updateEmployeePay(id: number, data: Record<string, unknown>): Promise<EmployeePay> {
  if (isMockEnabled()) return adminMock.updateEmployeePay(id, data as any);
  return put<EmployeePay>(`/cmp/employee-pays/${id}`, data);
}

/** 删除员工薪酬档（DELETE /cmp/employee-pays/{id}） */
export function deleteEmployeePay(id: number): Promise<void> {
  if (isMockEnabled()) return adminMock.removeEmployeePay(id);
  return del<void>(`/cmp/employee-pays/${id}`);
}

/* ---------------------------- 算薪取数预览（跨域） ---------------------------- */

/** 组合员工主档 + 考勤月结/日明细，生成算薪取数预览行 */
export function previewPayroll(periodCode: string, empIds: number[]): Promise<PayrollPreviewLine[]> {
  if (isMockEnabled()) return adminMock.payrollPreview(periodCode, empIds);
  return post<PayrollPreviewLine[]>('/cmp/payroll-preview', { periodCode, empIds });
}

/* ---------------------------- 期间关账（二期批 2） ---------------------------- */

/** 关账记录查询条件（均可选）：active=true 仅看锁定中，periodCode 精确匹配 */
export interface PeriodLockQuery {
  active?: boolean;
  periodCode?: string;
}

/** 分页查询期间关账记录（GET /cmp/period-locks?active=&periodCode=） */
export function listPeriodLocks(
  query: PeriodLockQuery,
  page: number,
  size: number,
): Promise<PageResult<PeriodLock>> {
  return get<PageResult<PeriodLock>>('/cmp/period-locks', { ...query, page, size });
}

/** 期间关账（POST /cmp/period-locks/{periodCode}/lock）：锁定后该期间 run 的 submit/update 被拒 */
export function lockPeriod(periodCode: string): Promise<PeriodLock> {
  return post<PeriodLock>(`/cmp/period-locks/${encodeURIComponent(periodCode)}/lock`);
}

/** 期间解锁（POST /cmp/period-locks/{periodCode}/unlock）：reason 必填，落审计 */
export function unlockPeriod(periodCode: string, reason: string): Promise<PeriodLock> {
  return post<PeriodLock>(`/cmp/period-locks/${encodeURIComponent(periodCode)}/unlock`, { reason });
}

