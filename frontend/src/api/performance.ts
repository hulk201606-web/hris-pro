/**
 * 绩效域 API 封装（后端 hris-performance 模块）。
 * 覆盖：考核周期（cycles）CRUD、目标（goals）CRUD、
 *       评估上下文取数（跨域拉员工档案简报展示汇报线）。
 */
import { del, get, post, put } from './http';
import { isMockEnabled } from './mock';
import { adminMock } from './admin-mock';
import type { PageResult } from '../types/api';
import type { Cycle, EmployeeProfileBrief, Goal } from '../types/admin';
import type { InterviewRecord, PipStatus } from '../types/api-batch2';

/* ---------------------------- 考核周期 ---------------------------- */

/** 分页查询考核周期（GET /prf/cycles） */
export function listCycles(page: number, size: number): Promise<PageResult<Cycle>> {
  if (isMockEnabled()) return adminMock.listCycles(page, size);
  return get<PageResult<Cycle>>('/prf/cycles', { page, size });
}

/** 新建考核周期（POST /prf/cycles） */
export function createCycle(data: Record<string, unknown>): Promise<Cycle> {
  if (isMockEnabled()) return adminMock.createCycle(data as any);
  return post<Cycle>('/prf/cycles', data);
}

/** 更新考核周期（PUT /prf/cycles/{id}） */
export function updateCycle(id: number, data: Record<string, unknown>): Promise<Cycle> {
  if (isMockEnabled()) return adminMock.updateCycle(id, data as any);
  return put<Cycle>(`/prf/cycles/${id}`, data);
}

/** 删除考核周期（DELETE /prf/cycles/{id}） */
export function deleteCycle(id: number): Promise<void> {
  if (isMockEnabled()) return adminMock.removeCycle(id);
  return del<void>(`/prf/cycles/${id}`);
}

/* ---------------------------- 目标 ---------------------------- */

/** 分页查询目标（GET /prf/goals） */
export function listGoals(page: number, size: number): Promise<PageResult<Goal>> {
  if (isMockEnabled()) return adminMock.listGoals(page, size);
  return get<PageResult<Goal>>('/prf/goals', { page, size });
}

/** 新建目标（POST /prf/goals） */
export function createGoal(data: Record<string, unknown>): Promise<Goal> {
  if (isMockEnabled()) return adminMock.createGoal(data as any);
  return post<Goal>('/prf/goals', data);
}

/** 更新目标（PUT /prf/goals/{id}） */
export function updateGoal(id: number, data: Record<string, unknown>): Promise<Goal> {
  if (isMockEnabled()) return adminMock.updateGoal(id, data as any);
  return put<Goal>(`/prf/goals/${id}`, data);
}

/** 删除目标（DELETE /prf/goals/{id}） */
export function deleteGoal(id: number): Promise<void> {
  if (isMockEnabled()) return adminMock.removeGoal(id);
  return del<void>(`/prf/goals/${id}`);
}

/* ---------------------------- 面谈记录（二期批 2） ---------------------------- */

/** 面谈记录查询条件（均可选） */
export interface InterviewQuery {
  cycleId?: number;
  empId?: number;
}

/** 分页查询面谈记录（GET /prf/interviews?cycleId=&empId=） */
export function listInterviews(
  query: InterviewQuery,
  page: number,
  size: number,
): Promise<PageResult<InterviewRecord>> {
  return get<PageResult<InterviewRecord>>('/prf/interviews', { ...query, page, size });
}

/** 新建面谈记录（POST /prf/interviews）；conclusion 为 PIP 时 pip 起止日期必填（服务端校验） */
export function createInterview(data: Record<string, unknown>): Promise<InterviewRecord> {
  return post<InterviewRecord>('/prf/interviews', data);
}

/** 更新面谈记录（PUT /prf/interviews/{id}） */
export function updateInterview(
  id: number,
  data: Record<string, unknown>,
): Promise<InterviewRecord> {
  return put<InterviewRecord>(`/prf/interviews/${id}`, data);
}

/** 登记 PIP 结果（POST /prf/interviews/{id}/pip-result）：仅 conclusion 为 PIP 的记录 */
export function submitPipResult(id: number, pipStatus: PipStatus): Promise<InterviewRecord> {
  return post<InterviewRecord>(`/prf/interviews/${id}/pip-result`, { pipStatus });
}

/** 我的面谈记录（GET /prf/interviews/me）：后端按当前登录员工过滤，返回分页 */
export function listMyInterviews(page = 1, size = 50): Promise<PageResult<InterviewRecord>> {
  return get<PageResult<InterviewRecord>>('/prf/interviews/me', { page, size });
}

/* ---------------------------- 评估上下文（跨域） ---------------------------- */

/** 拉取员工档案简报（部门/汇报线/在职状态），用于评估单上下文展示 */
export function fetchReviewContext(empId: number): Promise<EmployeeProfileBrief> {
  if (isMockEnabled()) return adminMock.reviewContext(empId);
  return get<EmployeeProfileBrief>('/prf/review-context', { empId });
}
