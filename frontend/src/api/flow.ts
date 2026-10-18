import { get, post } from './http';
import { isMockEnabled, mockApi } from './mock';
import type {
  PageResult,
  StartInstanceRequest,
  StartInstanceResponse,
  TaskActionRequest,
  TaskView,
} from '../types/api';

/** 我的待办（GET /flow/tasks/todo，分页聚合） */
export function fetchTodoTasks(page: number, size: number): Promise<PageResult<TaskView>> {
  if (isMockEnabled()) return mockApi.todoTasks(page, size);
  return get<PageResult<TaskView>>('/flow/tasks/todo', { page, size });
}

/** 我发起的（GET /flow/tasks/started，分页聚合） */
export function fetchStartedTasks(page: number, size: number): Promise<PageResult<TaskView>> {
  if (isMockEnabled()) return mockApi.startedTasks(page, size);
  return get<PageResult<TaskView>>('/flow/tasks/started', { page, size });
}

/** 发起流程实例（POST /flow/instances） */
export function startInstance(req: StartInstanceRequest): Promise<StartInstanceResponse> {
  if (isMockEnabled()) return mockApi.startInstance(req);
  return post<StartInstanceResponse>('/flow/instances', req);
}

/** 审批通过（POST /flow/tasks/{id}/approve） */
export function approveTask(id: string, req?: TaskActionRequest): Promise<void> {
  if (isMockEnabled()) return Promise.resolve();
  return post<void>(`/flow/tasks/${id}/approve`, req);
}

/** 审批驳回（POST /flow/tasks/{id}/reject） */
export function rejectTask(id: string, req?: TaskActionRequest): Promise<void> {
  if (isMockEnabled()) return Promise.resolve();
  return post<void>(`/flow/tasks/${id}/reject`, req);
}
