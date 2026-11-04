/**
 * 任务调度中心 API（PLT-F-011）。
 * 单独成文件：调度域端点独立，且平台主 API 文件已接近行数门禁上限。
 */
import { get, post, put } from './http';
import type { PageResult } from '../types/api';

/** 注册任务（scheduled 表示运行时是否已装载成功） */
export interface SchedulerTask {
  id: number;
  taskKey: string;
  cron: string;
  handler: string;
  status: number;
  scheduled: boolean;
  updatedAt: string;
}

/** 执行日志 */
export interface SchedulerLog {
  id: number;
  taskId: number;
  taskKey: string;
  handler: string;
  triggerType: string;
  status: string;
  retryNo: number;
  durationMs: number;
  message: string;
  createdAt: string;
}

/** 可注册的处理器白名单 */
export interface HandlerOption {
  key: string;
  description: string;
}

/** 手动触发结果 */
export interface RunResult {
  taskKey: string;
  status: string;
  message: string;
}

export function listSchedulerTasks(): Promise<SchedulerTask[]> {
  return get<SchedulerTask[]>('/plt/scheduler/tasks');
}

export function createSchedulerTask(data: Record<string, unknown>): Promise<SchedulerTask> {
  return post<SchedulerTask>('/plt/scheduler/tasks', data);
}

export function updateSchedulerTask(id: number, data: Record<string, unknown>): Promise<SchedulerTask> {
  return put<SchedulerTask>(`/plt/scheduler/tasks/${id}`, data);
}

export function runSchedulerTask(id: number): Promise<RunResult> {
  return post<RunResult>(`/plt/scheduler/tasks/${id}/run`, {});
}

export function listSchedulerLogs(taskKey?: string): Promise<PageResult<SchedulerLog>> {
  return get<PageResult<SchedulerLog>>('/plt/scheduler/logs', { taskKey, page: 1, size: 20 });
}

export function listSchedulerHandlers(): Promise<HandlerOption[]> {
  return get<HandlerOption[]>('/plt/scheduler/handlers');
}
