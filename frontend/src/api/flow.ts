import { get, post, del } from './http';
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

/* ---------------------------- 流程模型 ---------------------------- */

/** 流程模型（分页） */
export interface FlowModel {
  id: number;
  procDefKey: string;
  version: number;
  /** 部署状态：DRAFT / DEPLOYED 等 */
  deployState: string;
  createdAt?: string;
}

/** 流程轨迹节点 */
export interface FlowTrackNode {
  nodeId: string;
  nodeName: string;
  type: string;
  assignee?: string;
  status: string;
  startTime?: string;
  endTime?: string;
  durationInMillis?: number;
}

/** 流程轨迹 */
export interface FlowTrack {
  instanceId: string;
  status: string;
  startUserId?: string;
  startTime?: string;
  endTime?: string;
  nodes: FlowTrackNode[];
}

/** 模型分页（GET /flow/models） */
export function listModels(page: number, size: number): Promise<PageResult<FlowModel>> {
  if (isMockEnabled()) return Promise.resolve({ records: [], page, size, total: 0 });
  return get<PageResult<FlowModel>>('/flow/models', { page, size });
}

/** 新建模型（POST /flow/models），需提供 BPMN XML 内容 */
export function createModel(data: Record<string, unknown>): Promise<FlowModel> {
  return post<FlowModel>('/flow/models', data);
}

/** 部署模型（POST /flow/models/{id}/deploy） */
export function deployModel(id: number): Promise<FlowModel> {
  return post<FlowModel>(`/flow/models/${id}/deploy`);
}

/** 模型版本列表（GET /flow/models/{id}/versions） */
export function listModelVersions(id: number): Promise<FlowModel[]> {
  return get<FlowModel[]>(`/flow/models/${id}/versions`);
}

/* ---------------------------- 任务查询（补充） ---------------------------- */

/** 我的已办（GET /flow/tasks/done） */
export function fetchDoneTasks(page: number, size: number): Promise<PageResult<TaskView>> {
  return get<PageResult<TaskView>>('/flow/tasks/done', { page, size });
}

/** 抄送我的（GET /flow/tasks/cc） */
export function fetchCcTasks(page: number, size: number): Promise<PageResult<TaskView>> {
  return get<PageResult<TaskView>>('/flow/tasks/cc', { page, size });
}

/** 无主任务（GET /flow/tasks/unassigned）：本租户 assignee 为空的任务，procDefKey 可选过滤 */
export function fetchUnassignedTasks(
  page: number,
  size: number,
  procDefKey?: string,
): Promise<PageResult<TaskView>> {
  return get<PageResult<TaskView>>('/flow/tasks/unassigned', {
    page,
    size,
    ...(procDefKey ? { procDefKey } : {}),
  });
}

/* ---------------------------- 任务操作（补充） ---------------------------- */

/** 转办（POST /flow/tasks/{id}/transfer），需指定目标用户 */
export function transferTask(id: string, targetUserId: string, comment?: string): Promise<void> {
  return post<void>(`/flow/tasks/${id}/transfer`, { targetUserId, comment });
}

/** 加签（POST /flow/tasks/{id}/add-sign），需指定目标用户 */
export function addSignTask(id: string, targetUserId: string, comment?: string): Promise<void> {
  return post<void>(`/flow/tasks/${id}/add-sign`, { targetUserId, comment });
}

/** 撤回（POST /flow/tasks/{id}/withdraw）：仅首节点未处理时可用 */
export function withdrawTask(id: string, comment?: string): Promise<void> {
  return post<void>(`/flow/tasks/${id}/withdraw`, { comment });
}

/** 认领无主任务（POST /flow/tasks/{id}/claim）：assignee 置为操作者，重复认领自己幂等 */
export function claimTask(id: string, comment?: string): Promise<boolean> {
  return post<boolean>(`/flow/tasks/${id}/claim`, comment ? { comment } : undefined);
}

/** 指派（POST /flow/tasks/{id}/assign）：管理动作，强制把任务指给 targetUserId */
export function assignTask(id: string, targetUserId: number, comment?: string): Promise<void> {
  return post<void>(`/flow/tasks/${id}/assign`, { targetUserId, comment });
}

/* ---------------------------- 实例 ---------------------------- */

/** 流程轨迹（GET /flow/instances/{id}/track） */
export function getInstanceTrack(instanceId: string): Promise<FlowTrack> {
  return get<FlowTrack>(`/flow/instances/${instanceId}/track`);
}

/** 终止实例（DELETE /flow/instances/{id}），需 flow:instance:terminate */
export function terminateInstance(instanceId: string): Promise<void> {
  return del<void>(`/flow/instances/${instanceId}`);
}

/* ---------------------------- 委托代理 ---------------------------- */

/**
 * 委托代理记录（对齐 /flow/delegates 契约）。
 * 注意：后端并行开发中，字段以委派 Controller 就绪后的 DTO 为准，取数已做可选兜底。
 */
export interface FlowDelegate {
  id: number;
  /** 委托人（当前用户） */
  fromUser?: number;
  /** 被委托人 */
  toUser: number;
  /** 可选：仅委托特定流程定义 */
  procDefKey?: string;
  startAt: string;
  endAt: string;
  reason?: string;
  status?: string;
  createdAt?: string;
}

/** 我的委托列表（GET /flow/delegates/mine） */
export function listMyDelegates(): Promise<FlowDelegate[]> {
  return get<FlowDelegate[]>('/flow/delegates/mine');
}

/** 新建委托（POST /flow/delegates）：生效期内被委托人可代处理我的待办 */
export function createDelegate(data: {
  toUser: number;
  procDefKey?: string;
  startAt: string;
  endAt: string;
  reason?: string;
}): Promise<FlowDelegate> {
  return post<FlowDelegate>('/flow/delegates', data);
}

/** 提前结束委托（POST /flow/delegates/{id}/end） */
export function endDelegate(id: number): Promise<void> {
  return post<void>(`/flow/delegates/${id}/end`);
}
