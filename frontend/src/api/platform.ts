/**
 * 平台支撑域 API 封装（后端 hris-platform 模块）。
 * 覆盖：审计日志、系统配置、数据字典（类型 + 字典项）、通知模板、报表定义与导出。
 *
 * 注意两处与其他模块不同的接口形态：
 * 1. 系统配置以 **configKey 字符串** 定位（PUT/DELETE /plt/configs/{key}），
 *    不是数值 id；
 * 2. 字典项挂在类型下（GET /plt/dicts/types/{typeCode}/items）。
 */
import { get, post, put, del } from './http';
import type { PageResult } from '../types/api';
import type {
  AuditLogResponse,
  ConfigResponse,
  DictItemResponse,
  DictTypeResponse,
  ReportDefResponse,
  TemplateResponse,
} from '../types/api';

/* ---------------------------- 审计日志 ---------------------------- */

/** 审计日志分页查询（GET /plt/audit-logs）：服务端按当前租户强制隔离 */
export function listAuditLogs(page: number, size: number): Promise<PageResult<AuditLogResponse>> {
  return get<PageResult<AuditLogResponse>>('/plt/audit-logs', { page, size });
}

/* ---------------------------- 系统配置 ---------------------------- */

/** 全部配置项（GET /plt/configs） */
export function listConfigs(): Promise<ConfigResponse[]> {
  return get<ConfigResponse[]>('/plt/configs');
}

/** 单条配置（GET /plt/configs/{key}） */
export function getConfig(key: string): Promise<ConfigResponse> {
  return get<ConfigResponse>(`/plt/configs/${encodeURIComponent(key)}`);
}

/** 保存配置（PUT /plt/configs/{key}）：upsert 语义，key 不存在则新建 */
export function saveConfig(key: string, configValue: string): Promise<ConfigResponse> {
  return put<ConfigResponse>(`/plt/configs/${encodeURIComponent(key)}`, { configValue });
}

/** 删除配置（DELETE /plt/configs/{key}） */
export function deleteConfig(key: string): Promise<void> {
  return del<void>(`/plt/configs/${encodeURIComponent(key)}`);
}

/* ---------------------------- 数据字典 ---------------------------- */

/** 字典类型列表（GET /plt/dicts/types） */
export function listDictTypes(): Promise<DictTypeResponse[]> {
  return get<DictTypeResponse[]>('/plt/dicts/types');
}

/** 新建字典类型（POST /plt/dicts/types） */
export function createDictType(data: Record<string, unknown>): Promise<DictTypeResponse> {
  return post<DictTypeResponse>('/plt/dicts/types', data);
}

/** 更新字典类型（PUT /plt/dicts/types/{id}） */
export function updateDictType(id: number, data: Record<string, unknown>): Promise<DictTypeResponse> {
  return put<DictTypeResponse>(`/plt/dicts/types/${id}`, data);
}

/** 删除字典类型（DELETE /plt/dicts/types/{id}） */
export function deleteDictType(id: number): Promise<void> {
  return del<void>(`/plt/dicts/types/${id}`);
}

/** 某类型下的字典项（GET /plt/dicts/types/{typeCode}/items） */
export function listDictItems(typeCode: string): Promise<DictItemResponse[]> {
  return get<DictItemResponse[]>(`/plt/dicts/types/${encodeURIComponent(typeCode)}/items`);
}

/** 新增字典项（POST /plt/dicts/types/{typeCode}/items） */
export function createDictItem(
  typeCode: string,
  data: Record<string, unknown>,
): Promise<DictItemResponse> {
  return post<DictItemResponse>(`/plt/dicts/types/${encodeURIComponent(typeCode)}/items`, data);
}

/** 更新字典项（PUT /plt/dicts/items/{id}）：注意路径不带 typeCode */
export function updateDictItem(id: number, data: Record<string, unknown>): Promise<DictItemResponse> {
  return put<DictItemResponse>(`/plt/dicts/items/${id}`, data);
}

/** 删除字典项（DELETE /plt/dicts/items/{id}） */
export function deleteDictItem(id: number): Promise<void> {
  return del<void>(`/plt/dicts/items/${id}`);
}

/* ---------------------------- 通知模板 ---------------------------- */

/** 模板列表（GET /plt/notifications/templates） */
export function listTemplates(): Promise<TemplateResponse[]> {
  return get<TemplateResponse[]>('/plt/notifications/templates');
}

/** 新建模板（POST /plt/notifications/templates） */
export function createTemplate(data: Record<string, unknown>): Promise<TemplateResponse> {
  return post<TemplateResponse>('/plt/notifications/templates', data);
}

/** 更新模板（PUT /plt/notifications/templates/{id}） */
export function updateTemplate(id: number, data: Record<string, unknown>): Promise<TemplateResponse> {
  return put<TemplateResponse>(`/plt/notifications/templates/${id}`, data);
}

/** 删除模板（DELETE /plt/notifications/templates/{id}） */
export function deleteTemplate(id: number): Promise<void> {
  return del<void>(`/plt/notifications/templates/${id}`);
}

/* ---------------------------- 报表中心 ---------------------------- */

/** 报表定义列表（GET /plt/reports/defs） */
export function listReportDefs(): Promise<ReportDefResponse[]> {
  return get<ReportDefResponse[]>('/plt/reports/defs');
}

/** 新建报表定义（POST /plt/reports/defs） */
export function createReportDef(data: Record<string, unknown>): Promise<ReportDefResponse> {
  return post<ReportDefResponse>('/plt/reports/defs', data);
}

/** 更新报表定义（PUT /plt/reports/defs/{id}） */
export function updateReportDef(id: number, data: Record<string, unknown>): Promise<ReportDefResponse> {
  return put<ReportDefResponse>(`/plt/reports/defs/${id}`, data);
}

/** 删除报表定义（DELETE /plt/reports/defs/{id}） */
export function deleteReportDef(id: number): Promise<void> {
  return del<void>(`/plt/reports/defs/${id}`);
}

/* ---------------------------- 导出任务 ---------------------------- */

/** 导出任务（报表定义 → 异步生成文件） */
export interface ExportTaskResponse {
  id: number;
  reportDefId: number;
  /** 任务状态：PENDING / DONE / FAILED */
  status: string;
  fileId?: number;
  createdAt?: string;
}

/** 导出任务分页（GET /plt/reports/exports） */
export function listExports(
  page: number,
  size: number,
  status?: string,
): Promise<PageResult<ExportTaskResponse>> {
  const params: Record<string, unknown> = { page, size };
  if (status) params.status = status;
  return get<PageResult<ExportTaskResponse>>('/plt/reports/exports', params);
}

/** 发起导出（POST /plt/reports/exports）：需 plt:report:export 权限 */
export function createExport(reportDefId: number): Promise<ExportTaskResponse> {
  return post<ExportTaskResponse>('/plt/reports/exports', { reportDefId });
}
