import { del, get, post, put } from './http';
import { isMockEnabled } from './mock';
import { adminMock } from './admin-mock';
import type { PageResult } from '../types/api';
import type { Cycle, EmployeeProfileBrief, Goal } from '../types/admin';

/* ---------------------------- 考核周期 ---------------------------- */

export function listCycles(page: number, size: number): Promise<PageResult<Cycle>> {
  if (isMockEnabled()) return adminMock.listCycles(page, size);
  return get<PageResult<Cycle>>('/prf/cycles', { page, size });
}

export function createCycle(data: Record<string, unknown>): Promise<Cycle> {
  if (isMockEnabled()) return adminMock.createCycle(data as any);
  return post<Cycle>('/prf/cycles', data);
}

export function updateCycle(id: number, data: Record<string, unknown>): Promise<Cycle> {
  if (isMockEnabled()) return adminMock.updateCycle(id, data as any);
  return put<Cycle>(`/prf/cycles/${id}`, data);
}

export function deleteCycle(id: number): Promise<void> {
  if (isMockEnabled()) return adminMock.removeCycle(id);
  return del<void>(`/prf/cycles/${id}`);
}

/* ---------------------------- 目标 ---------------------------- */

export function listGoals(page: number, size: number): Promise<PageResult<Goal>> {
  if (isMockEnabled()) return adminMock.listGoals(page, size);
  return get<PageResult<Goal>>('/prf/goals', { page, size });
}

export function createGoal(data: Record<string, unknown>): Promise<Goal> {
  if (isMockEnabled()) return adminMock.createGoal(data as any);
  return post<Goal>('/prf/goals', data);
}

export function updateGoal(id: number, data: Record<string, unknown>): Promise<Goal> {
  if (isMockEnabled()) return adminMock.updateGoal(id, data as any);
  return put<Goal>(`/prf/goals/${id}`, data);
}

export function deleteGoal(id: number): Promise<void> {
  if (isMockEnabled()) return adminMock.removeGoal(id);
  return del<void>(`/prf/goals/${id}`);
}

/* ---------------------------- 评估上下文（跨域） ---------------------------- */

/** 拉取员工档案简报（部门/汇报线/在职状态），用于评估单上下文展示 */
export function fetchReviewContext(empId: number): Promise<EmployeeProfileBrief> {
  if (isMockEnabled()) return adminMock.reviewContext(empId);
  return get<EmployeeProfileBrief>('/prf/review-context', { empId });
}
