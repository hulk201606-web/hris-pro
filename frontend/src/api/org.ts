/**
 * 组织架构域 API 封装（后端 hris-org 模块）。
 * 覆盖：法人主体 / 部门树 / 岗位 / 职位 / 职级 / 职级带 / 编制配额 的
 * 列表、增改、历史快照与组织调整申请。
 *
 * 说明：本模块的 GET 端点均返回 List（非分页），故前端统一用 SimpleCrudPage。
 * 所有请求走统一 http 实例，自动携带 Bearer Token 并解包业务响应体。
 */
import { get, post, put, del } from './http';
import type { CatalogResponse, DeptNode, HeadcountUsage } from '../types/api';
import type { HeadcountStructureResponse } from '../types/api-batch3';

/* ---------------------------- 法人主体 ---------------------------- */

/** 查询法人主体列表（GET /org/legal-entities） */
export function listLegalEntities(): Promise<CatalogResponse[]> {
  return get<CatalogResponse[]>('/org/legal-entities');
}

/** 新建法人主体（POST /org/legal-entities） */
export function createLegalEntity(data: Record<string, unknown>): Promise<CatalogResponse> {
  return post<CatalogResponse>('/org/legal-entities', data);
}

/** 更新法人主体（PUT /org/legal-entities/{id}） */
export function updateLegalEntity(id: number, data: Record<string, unknown>): Promise<CatalogResponse> {
  return put<CatalogResponse>(`/org/legal-entities/${id}`, data);
}

/* ---------------------------- 部门树 ---------------------------- */

/** 查询部门（GET /org/depts，tree=true 返回嵌套树） */
export function listDepts(tree = false): Promise<DeptNode[]> {
  return get<DeptNode[]>('/org/depts', { tree });
}

/** 新建部门（POST /org/depts） */
export function createDept(data: Record<string, unknown>): Promise<DeptNode> {
  return post<DeptNode>('/org/depts', data);
}

/** 更新部门（PUT /org/depts/{id}），变更前服务端自动写历史快照 */
export function updateDept(id: number, data: Record<string, unknown>): Promise<DeptNode> {
  return put<DeptNode>(`/org/depts/${id}`, data);
}

/**
 * 部门移动 + 兄弟排序（POST /org/depts/{id}/move，ORG-F-002 深化拖拽排序）。
 * newParentId 为空/0 表示移动为根；sortOrder 为目标兄弟位次。
 */
export function moveDept(
  id: number,
  newParentId: number | null,
  sortOrder: number,
): Promise<DeptNode> {
  return post<DeptNode>(`/org/depts/${id}/move`, { newParentId, sortOrder });
}

/** 部门历史快照（GET /org/depts/{id}/snapshot） */
export function getDeptSnapshot(id: number, at?: string): Promise<Record<string, unknown>> {
  return get<Record<string, unknown>>(`/org/depts/${id}/snapshot`, at ? { at } : undefined);
}

/* ---------------------------- 岗位 / 职位 / 职级 / 职级带 ---------------------------- */

export function listPositions(): Promise<CatalogResponse[]> {
  return get<CatalogResponse[]>('/org/positions');
}
export function createPosition(data: Record<string, unknown>): Promise<CatalogResponse> {
  return post<CatalogResponse>('/org/positions', data);
}
export function updatePosition(id: number, data: Record<string, unknown>): Promise<CatalogResponse> {
  return put<CatalogResponse>(`/org/positions/${id}`, data);
}
export function deletePosition(id: number): Promise<void> {
  return del<void>(`/org/positions/${id}`);
}

/** 查询岗位挂靠的部门 ID 列表（GET /org/positions/{id}/depts） */
export function listPositionDepts(id: number): Promise<number[]> {
  return get<number[]>(`/org/positions/${id}/depts`);
}

/** 岗位×部门挂靠整体替换（POST /org/positions/{id}/depts），空列表即清空，幂等 */
export function replacePositionDepts(id: number, deptIds: number[]): Promise<number[]> {
  return post<number[]>(`/org/positions/${id}/depts`, { deptIds });
}

export function listJobs(): Promise<CatalogResponse[]> {
  return get<CatalogResponse[]>('/org/jobs');
}
export function createJob(data: Record<string, unknown>): Promise<CatalogResponse> {
  return post<CatalogResponse>('/org/jobs', data);
}
export function updateJob(id: number, data: Record<string, unknown>): Promise<CatalogResponse> {
  return put<CatalogResponse>(`/org/jobs/${id}`, data);
}
export function deleteJob(id: number): Promise<void> {
  return del<void>(`/org/jobs/${id}`);
}

export function listGrades(): Promise<CatalogResponse[]> {
  return get<CatalogResponse[]>('/org/grades');
}
export function createGrade(data: Record<string, unknown>): Promise<CatalogResponse> {
  return post<CatalogResponse>('/org/grades', data);
}
export function updateGrade(id: number, data: Record<string, unknown>): Promise<CatalogResponse> {
  return put<CatalogResponse>(`/org/grades/${id}`, data);
}
export function deleteGrade(id: number): Promise<void> {
  return del<void>(`/org/grades/${id}`);
}

export function listGradeBands(): Promise<CatalogResponse[]> {
  return get<CatalogResponse[]>('/org/bands');
}
export function createGradeBand(data: Record<string, unknown>): Promise<CatalogResponse> {
  return post<CatalogResponse>('/org/bands', data);
}
export function updateGradeBand(id: number, data: Record<string, unknown>): Promise<CatalogResponse> {
  return put<CatalogResponse>(`/org/bands/${id}`, data);
}
export function deleteGradeBand(id: number): Promise<void> {
  return del<void>(`/org/bands/${id}`);
}

/* ---------------------------- 编制 ---------------------------- */

/** 编制使用情况（GET /org/headcounts 与 /org/headcounts/usage 同结构） */
export function listHeadcounts(): Promise<HeadcountUsage[]> {
  return get<HeadcountUsage[]>('/org/headcounts');
}

/** 设置编制配额（PUT /org/headcounts，需 org:headcount:write） */
export function updateHeadcount(data: Record<string, unknown>): Promise<HeadcountUsage> {
  return put<HeadcountUsage>('/org/headcounts', data);
}

/** 编制使用情况（GET /org/headcounts/usage）：可按部门与期间过滤（编制看板用） */
export function listHeadcountUsage(deptId?: number, period?: string): Promise<HeadcountUsage[]> {
  const params: Record<string, unknown> = {};
  if (deptId) params.deptId = deptId;
  if (period) params.period = period;
  return get<HeadcountUsage[]>('/org/headcounts/usage', params);
}

/* ---------------------------- 报表（二期批 3） ---------------------------- */

/**
 * 人力结构分析（GET /org/reports/headcount-structure，P1 聚合报表）。
 * deptScope=top 一级部门滚动聚合（默认）；all 逐部门。
 */
export function getHeadcountStructure(deptScope: string): Promise<HeadcountStructureResponse> {
  return get<HeadcountStructureResponse>('/org/reports/headcount-structure', { deptScope });
}
