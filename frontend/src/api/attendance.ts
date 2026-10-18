import { get, post, put } from './http';
import { isMockEnabled } from './mock';
import { attendanceMock } from './attendance-mock';
import type { PageResult, AttRequestQuery, FixCreateRequest, FixRequest, LeaveCreateRequest, LeaveRequest, OvertimeCreateRequest, OvertimeRequest } from '../types/api';
import type {
  AttException,
  AttGroup,
  AttLeaveType,
  AttPayrollCycle,
  AttQueryParams,
  AttShift,
  DailyAttendance,
  DailyComputeResult,
  ExceptionHandleResult,
} from '../types/admin';

/* ---------------------------- 考勤组 ---------------------------- */

export function listGroups(): Promise<AttGroup[]> {
  if (isMockEnabled()) return attendanceMock.listGroups();
  return get<AttGroup[]>('/att/groups');
}

export function createGroup(data: Record<string, unknown>): Promise<AttGroup> {
  if (isMockEnabled()) return attendanceMock.createGroup(data as any);
  return post<AttGroup>('/att/groups', data);
}

export function updateGroup(id: number, data: Record<string, unknown>): Promise<AttGroup> {
  if (isMockEnabled()) return attendanceMock.updateGroup(id, data as any);
  return put<AttGroup>(`/att/groups/${id}`, data);
}

/* ---------------------------- 班次 ---------------------------- */

export function listShifts(): Promise<AttShift[]> {
  if (isMockEnabled()) return attendanceMock.listShifts();
  return get<AttShift[]>('/att/shifts');
}

export function createShift(data: Record<string, unknown>): Promise<AttShift> {
  if (isMockEnabled()) return attendanceMock.createShift(data as any);
  return post<AttShift>('/att/shifts', data);
}

export function updateShift(id: number, data: Record<string, unknown>): Promise<AttShift> {
  if (isMockEnabled()) return attendanceMock.updateShift(id, data as any);
  return put<AttShift>(`/att/shifts/${id}`, data);
}

/* ---------------------------- 假期类型 ---------------------------- */

export function listLeaveTypes(): Promise<AttLeaveType[]> {
  if (isMockEnabled()) return attendanceMock.listLeaveTypes();
  return get<AttLeaveType[]>('/att/leave-types');
}

export function createLeaveType(data: Record<string, unknown>): Promise<AttLeaveType> {
  if (isMockEnabled()) return attendanceMock.createLeaveType(data as any);
  return post<AttLeaveType>('/att/leave-types', data);
}

export function updateLeaveType(id: number, data: Record<string, unknown>): Promise<AttLeaveType> {
  if (isMockEnabled()) return attendanceMock.updateLeaveType(id, data as any);
  return put<AttLeaveType>(`/att/leave-types/${id}`, data);
}

/* ---------------------------- 薪酬取数周期 ---------------------------- */

export function listPayrollCycles(): Promise<AttPayrollCycle[]> {
  if (isMockEnabled()) return attendanceMock.listPayrollCycles();
  return get<AttPayrollCycle[]>('/att/payroll-cycles');
}

export function createPayrollCycle(data: Record<string, unknown>): Promise<AttPayrollCycle> {
  if (isMockEnabled()) return attendanceMock.createPayrollCycle(data as any);
  return post<AttPayrollCycle>('/att/payroll-cycles', data);
}

export function updatePayrollCycle(id: number, data: Record<string, unknown>): Promise<AttPayrollCycle> {
  if (isMockEnabled()) return attendanceMock.updatePayrollCycle(id, data as any);
  return put<AttPayrollCycle>(`/att/payroll-cycles/${id}`, data);
}

/** 封存周期（封存后不可再变更） */
export function closePayrollCycle(id: number): Promise<AttPayrollCycle> {
  if (isMockEnabled()) return attendanceMock.closePayrollCycle(id);
  return post<AttPayrollCycle>(`/att/payroll-cycles/${id}/close`);
}

/* ---------------------------- 日考勤 ---------------------------- */

export function listDaily(
  params: AttQueryParams,
  page: number,
  size: number,
): Promise<PageResult<DailyAttendance>> {
  if (isMockEnabled()) return attendanceMock.listDaily(params, page, size);
  return get<PageResult<DailyAttendance>>('/att/daily', { ...params, page, size });
}

/** 触发日考勤计算（empIds 为空表示该日期全部排班员工重算） */
export function computeDaily(workDate: string, empIds: number[]): Promise<DailyComputeResult> {
  if (isMockEnabled()) return attendanceMock.computeDaily(workDate);
  return post<DailyComputeResult>('/att/daily/compute', { workDate, empIds });
}

/* ---------------------------- 考勤异常 ---------------------------- */

export function listExceptions(
  params: AttQueryParams,
  page: number,
  size: number,
): Promise<PageResult<AttException>> {
  if (isMockEnabled()) return attendanceMock.listExceptions(params, page, size);
  return get<PageResult<AttException>>('/att/exceptions', { ...params, page, size });
}

/** 处理异常：action = RESOLVE（认定）/ IGNORE（豁免） */
export function handleException(
  id: number,
  action: 'RESOLVE' | 'IGNORE',
  remark?: string,
): Promise<ExceptionHandleResult> {
  if (isMockEnabled()) return attendanceMock.handleException(id, action);
  return post<ExceptionHandleResult>(`/att/exceptions/${id}/handle`, { action, remark });
}

/** 批量处理异常 */
export function batchHandleExceptions(
  ids: number[],
  action: 'RESOLVE' | 'IGNORE',
  remark?: string,
): Promise<ExceptionHandleResult> {
  if (isMockEnabled()) return attendanceMock.batchHandleExceptions(ids, action);
  return post<ExceptionHandleResult>('/att/exceptions/batch-handle', { ids, action, remark });
}

/* ---------------------------- 请假单 ---------------------------- */

export function listLeaveRequests(
  params: AttRequestQuery,
  page: number,
  size: number,
): Promise<PageResult<LeaveRequest>> {
  if (isMockEnabled()) return attendanceMock.listLeaveRequests(params, page, size);
  return get<PageResult<LeaveRequest>>('/att/leave-requests', { ...params, page, size });
}

export function createLeaveRequest(data: LeaveCreateRequest): Promise<LeaveRequest> {
  if (isMockEnabled()) return attendanceMock.createLeaveRequest(data);
  return post<LeaveRequest>('/att/leave-requests', data);
}

export function approveLeaveRequest(id: number, remark?: string): Promise<LeaveRequest> {
  if (isMockEnabled()) return attendanceMock.approveLeaveRequest(id);
  return post<LeaveRequest>(`/att/leave-requests/${id}/approve`, { remark });
}

export function rejectLeaveRequest(id: number, remark?: string): Promise<LeaveRequest> {
  if (isMockEnabled()) return attendanceMock.rejectLeaveRequest(id);
  return post<LeaveRequest>(`/att/leave-requests/${id}/reject`, { remark });
}

/* ---------------------------- 加班单 ---------------------------- */

export function listOvertimeRequests(
  params: AttRequestQuery,
  page: number,
  size: number,
): Promise<PageResult<OvertimeRequest>> {
  if (isMockEnabled()) return attendanceMock.listOvertimeRequests(params, page, size);
  return get<PageResult<OvertimeRequest>>('/att/overtime-requests', { ...params, page, size });
}

export function createOvertimeRequest(data: OvertimeCreateRequest): Promise<OvertimeRequest> {
  if (isMockEnabled()) return attendanceMock.createOvertimeRequest(data);
  return post<OvertimeRequest>('/att/overtime-requests', data);
}

export function approveOvertimeRequest(id: number, remark?: string): Promise<OvertimeRequest> {
  if (isMockEnabled()) return attendanceMock.approveOvertimeRequest(id);
  return post<OvertimeRequest>(`/att/overtime-requests/${id}/approve`, { remark });
}

export function rejectOvertimeRequest(id: number, remark?: string): Promise<OvertimeRequest> {
  if (isMockEnabled()) return attendanceMock.rejectOvertimeRequest(id);
  return post<OvertimeRequest>(`/att/overtime-requests/${id}/reject`, { remark });
}

/* ---------------------------- 补卡单 ---------------------------- */

export function listFixRequests(
  params: AttRequestQuery,
  page: number,
  size: number,
): Promise<PageResult<FixRequest>> {
  if (isMockEnabled()) return attendanceMock.listFixRequests(params, page, size);
  return get<PageResult<FixRequest>>('/att/fix-requests', { ...params, page, size });
}

export function createFixRequest(data: FixCreateRequest): Promise<FixRequest> {
  if (isMockEnabled()) return attendanceMock.createFixRequest(data);
  return post<FixRequest>('/att/fix-requests', data);
}

export function approveFixRequest(id: number, remark?: string): Promise<FixRequest> {
  if (isMockEnabled()) return attendanceMock.approveFixRequest(id);
  return post<FixRequest>(`/att/fix-requests/${id}/approve`, { remark });
}

export function rejectFixRequest(id: number, remark?: string): Promise<FixRequest> {
  if (isMockEnabled()) return attendanceMock.rejectFixRequest(id);
  return post<FixRequest>(`/att/fix-requests/${id}/reject`, { remark });
}
