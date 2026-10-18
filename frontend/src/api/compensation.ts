import { del, get, post, put } from './http';
import { isMockEnabled } from './mock';
import { adminMock } from './admin-mock';
import type { PageResult } from '../types/api';
import type { EmployeePay, PayrollPreviewLine, PayrollRun } from '../types/admin';

/* ---------------------------- 薪酬核算批次 ---------------------------- */

export function listPayrollRuns(page: number, size: number): Promise<PageResult<PayrollRun>> {
  if (isMockEnabled()) return adminMock.listPayrollRuns(page, size);
  return get<PageResult<PayrollRun>>('/cmp/payroll-runs', { page, size });
}

export function createPayrollRun(data: Record<string, unknown>): Promise<PayrollRun> {
  if (isMockEnabled()) return adminMock.createPayrollRun(data as any);
  return post<PayrollRun>('/cmp/payroll-runs', data);
}

export function updatePayrollRun(id: number, data: Record<string, unknown>): Promise<PayrollRun> {
  if (isMockEnabled()) return adminMock.updatePayrollRun(id, data as any);
  return put<PayrollRun>(`/cmp/payroll-runs/${id}`, data);
}

export function deletePayrollRun(id: number): Promise<void> {
  if (isMockEnabled()) return adminMock.removePayrollRun(id);
  return del<void>(`/cmp/payroll-runs/${id}`);
}

/* ---------------------------- 员工薪酬档 ---------------------------- */

export function listEmployeePays(page: number, size: number): Promise<PageResult<EmployeePay>> {
  if (isMockEnabled()) return adminMock.listEmployeePays(page, size);
  return get<PageResult<EmployeePay>>('/cmp/employee-pays', { page, size });
}

export function createEmployeePay(data: Record<string, unknown>): Promise<EmployeePay> {
  if (isMockEnabled()) return adminMock.createEmployeePay(data as any);
  return post<EmployeePay>('/cmp/employee-pays', data);
}

export function updateEmployeePay(id: number, data: Record<string, unknown>): Promise<EmployeePay> {
  if (isMockEnabled()) return adminMock.updateEmployeePay(id, data as any);
  return put<EmployeePay>(`/cmp/employee-pays/${id}`, data);
}

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
