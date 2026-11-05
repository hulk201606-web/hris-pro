/**
 * 考勤域 API 封装（后端 hris-attendance 模块）。
 * 覆盖：考勤组 / 班次 / 假种 / 薪酬取数周期 / 日考勤计算 / 异常处理 /
 *       请假单 / 加班单 / 补卡单 的列表与发起、审批操作。
 * 所有请求走统一 http 实例，自动携带 Bearer Token 并解包业务响应体。
 */
import { get, post, put } from './http';
import { isMockEnabled } from './mock';
import { attendanceMock } from './attendance-mock';
import type { PageResult, AttRequestQuery, FixCreateRequest, FixRequest, LeaveCreateRequest, LeaveRequest, OvertimeCreateRequest, OvertimeRequest, TripCreateRequest, TripRequest } from '../types/api';
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

/** 查询考勤组列表（GET /att/groups） */
export function listGroups(): Promise<AttGroup[]> {
  if (isMockEnabled()) return attendanceMock.listGroups();
  return get<AttGroup[]>('/att/groups');
}

/** 新建考勤组（POST /att/groups） */
export function createGroup(data: Record<string, unknown>): Promise<AttGroup> {
  if (isMockEnabled()) return attendanceMock.createGroup(data as any);
  return post<AttGroup>('/att/groups', data);
}

/** 更新考勤组（PUT /att/groups/{id}） */
export function updateGroup(id: number, data: Record<string, unknown>): Promise<AttGroup> {
  if (isMockEnabled()) return attendanceMock.updateGroup(id, data as any);
  return put<AttGroup>(`/att/groups/${id}`, data);
}

/* ---------------------------- 考勤组成员（批 6 #10） ---------------------------- */

/** 组成员列表（GET /att/groups/{id}/members）：返回员工 ID 数组 */
export function listGroupMembers(groupId: number): Promise<number[]> {
  return get<number[]>(`/att/groups/${groupId}/members`);
}

/** 批量设置组成员（POST /att/groups/{id}/members）：全量覆盖语义，empIds 即最终成员 */
export function setGroupMembers(groupId: number, empIds: number[]): Promise<number[]> {
  return post<number[]>(`/att/groups/${groupId}/members`, { empIds });
}

/* ---------------------------- 班次 ---------------------------- */

/** 查询班次列表（GET /att/shifts） */
export function listShifts(): Promise<AttShift[]> {
  if (isMockEnabled()) return attendanceMock.listShifts();
  return get<AttShift[]>('/att/shifts');
}

/** 新建班次（POST /att/shifts） */
export function createShift(data: Record<string, unknown>): Promise<AttShift> {
  if (isMockEnabled()) return attendanceMock.createShift(data as any);
  return post<AttShift>('/att/shifts', data);
}

/** 更新班次（PUT /att/shifts/{id}） */
export function updateShift(id: number, data: Record<string, unknown>): Promise<AttShift> {
  if (isMockEnabled()) return attendanceMock.updateShift(id, data as any);
  return put<AttShift>(`/att/shifts/${id}`, data);
}

/* ---------------------------- 假期类型 ---------------------------- */

/** 查询假种列表（GET /att/leave-types） */
export function listLeaveTypes(): Promise<AttLeaveType[]> {
  if (isMockEnabled()) return attendanceMock.listLeaveTypes();
  return get<AttLeaveType[]>('/att/leave-types');
}

/** 新建假种（POST /att/leave-types） */
export function createLeaveType(data: Record<string, unknown>): Promise<AttLeaveType> {
  if (isMockEnabled()) return attendanceMock.createLeaveType(data as any);
  return post<AttLeaveType>('/att/leave-types', data);
}

/** 更新假种（PUT /att/leave-types/{id}） */
export function updateLeaveType(id: number, data: Record<string, unknown>): Promise<AttLeaveType> {
  if (isMockEnabled()) return attendanceMock.updateLeaveType(id, data as any);
  return put<AttLeaveType>(`/att/leave-types/${id}`, data);
}

/* ---------------------------- 薪酬取数周期 ---------------------------- */

/** 查询薪酬取数周期列表（GET /att/payroll-cycles） */
export function listPayrollCycles(): Promise<AttPayrollCycle[]> {
  if (isMockEnabled()) return attendanceMock.listPayrollCycles();
  return get<AttPayrollCycle[]>('/att/payroll-cycles');
}

/** 新建薪酬取数周期（POST /att/payroll-cycles） */
export function createPayrollCycle(data: Record<string, unknown>): Promise<AttPayrollCycle> {
  if (isMockEnabled()) return attendanceMock.createPayrollCycle(data as any);
  return post<AttPayrollCycle>('/att/payroll-cycles', data);
}

/** 更新薪酬取数周期（PUT /att/payroll-cycles/{id}） */
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

/** 分页查询日考勤明细（GET /att/daily） */
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

/** 分页查询考勤异常列表（GET /att/exceptions） */
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

/** 分页查询请假单列表（GET /att/leave-requests） */
export function listLeaveRequests(
  params: AttRequestQuery,
  page: number,
  size: number,
): Promise<PageResult<LeaveRequest>> {
  if (isMockEnabled()) return attendanceMock.listLeaveRequests(params, page, size);
  return get<PageResult<LeaveRequest>>('/att/leave-requests', { ...params, page, size });
}

/** 发起请假单（POST /att/leave-requests），后端自动触发流程引擎 */
export function createLeaveRequest(data: LeaveCreateRequest): Promise<LeaveRequest> {
  if (isMockEnabled()) return attendanceMock.createLeaveRequest(data);
  return post<LeaveRequest>('/att/leave-requests', data);
}

/** 审批通过请假单（POST /att/leave-requests/{id}/approve） */
export function approveLeaveRequest(id: number, remark?: string): Promise<LeaveRequest> {
  if (isMockEnabled()) return attendanceMock.approveLeaveRequest(id);
  return post<LeaveRequest>(`/att/leave-requests/${id}/approve`, { remark });
}

/** 驳回请假单（POST /att/leave-requests/{id}/reject） */
export function rejectLeaveRequest(id: number, remark?: string): Promise<LeaveRequest> {
  if (isMockEnabled()) return attendanceMock.rejectLeaveRequest(id);
  return post<LeaveRequest>(`/att/leave-requests/${id}/reject`, { remark });
}

/* ---------------------------- 加班单 ---------------------------- */

/** 分页查询加班单列表（GET /att/overtime-requests） */
export function listOvertimeRequests(
  params: AttRequestQuery,
  page: number,
  size: number,
): Promise<PageResult<OvertimeRequest>> {
  if (isMockEnabled()) return attendanceMock.listOvertimeRequests(params, page, size);
  return get<PageResult<OvertimeRequest>>('/att/overtime-requests', { ...params, page, size });
}

/** 发起加班单（POST /att/overtime-requests），后端自动触发流程引擎 */
export function createOvertimeRequest(data: OvertimeCreateRequest): Promise<OvertimeRequest> {
  if (isMockEnabled()) return attendanceMock.createOvertimeRequest(data);
  return post<OvertimeRequest>('/att/overtime-requests', data);
}

/** 审批通过加班单（POST /att/overtime-requests/{id}/approve） */
export function approveOvertimeRequest(id: number, remark?: string): Promise<OvertimeRequest> {
  if (isMockEnabled()) return attendanceMock.approveOvertimeRequest(id);
  return post<OvertimeRequest>(`/att/overtime-requests/${id}/approve`, { remark });
}

/** 驳回加班单（POST /att/overtime-requests/{id}/reject） */
export function rejectOvertimeRequest(id: number, remark?: string): Promise<OvertimeRequest> {
  if (isMockEnabled()) return attendanceMock.rejectOvertimeRequest(id);
  return post<OvertimeRequest>(`/att/overtime-requests/${id}/reject`, { remark });
}

/* ---------------------------- 补卡单 ---------------------------- */

/** 分页查询补卡单列表（GET /att/fix-requests） */
export function listFixRequests(
  params: AttRequestQuery,
  page: number,
  size: number,
): Promise<PageResult<FixRequest>> {
  if (isMockEnabled()) return attendanceMock.listFixRequests(params, page, size);
  return get<PageResult<FixRequest>>('/att/fix-requests', { ...params, page, size });
}

/** 发起补卡单（POST /att/fix-requests），后端自动触发流程引擎 */
export function createFixRequest(data: FixCreateRequest): Promise<FixRequest> {
  if (isMockEnabled()) return attendanceMock.createFixRequest(data);
  return post<FixRequest>('/att/fix-requests', data);
}

/** 审批通过补卡单（POST /att/fix-requests/{id}/approve） */
export function approveFixRequest(id: number, remark?: string): Promise<FixRequest> {
  if (isMockEnabled()) return attendanceMock.approveFixRequest(id);
  return post<FixRequest>(`/att/fix-requests/${id}/approve`, { remark });
}

/** 驳回补卡单（POST /att/fix-requests/{id}/reject） */
export function rejectFixRequest(id: number, remark?: string): Promise<FixRequest> {
  if (isMockEnabled()) return attendanceMock.rejectFixRequest(id);
  return post<FixRequest>(`/att/fix-requests/${id}/reject`, { remark });
}

/* ---------------------------- 出差单（ATT-F-017） ---------------------------- */

/** 分页查询出差单列表（GET /att/trip-requests） */
export function fetchTrips(
  params: AttRequestQuery,
  page: number,
  size: number,
): Promise<PageResult<TripRequest>> {
  return get<PageResult<TripRequest>>('/att/trip-requests', { ...params, page, size });
}

/** 发起出差单（POST /att/trip-requests），后端自动触发流程引擎 */
export function createTrip(data: TripCreateRequest): Promise<TripRequest> {
  return post<TripRequest>('/att/trip-requests', data);
}

/* ---------------------------- 报表（ATT-F-025） ---------------------------- */
/* 报表接口与行类型较多，拆分至 attendance-report.ts（ AttendanceRateItem 等同域类型） */
