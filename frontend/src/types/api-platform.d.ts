/**
 * 前后端共享接口类型契约 —— 平台支撑模块（hris-platform）
 *
 * 类型名与契约 schema 的对应关系（check-type-sync.py 按「模块前缀 + 名称」匹配）：
 *   ConfigResponse    ↔ PltConfigResponse
 *   DictTypeResponse  ↔ PltDictTypeResponse
 *   DictItemResponse  ↔ PltDictItemResponse
 *   AuditLogResponse  ↔ PltAuditLogResponse
 *   TemplateResponse  ↔ PltTemplateResponse
 *   ReportDefResponse ↔ PltReportDefResponse
 */

/** 租户级系统配置（key-value） */
export interface ConfigResponse {
  id: number;
  configKey: string;
  configValue: string;
  updatedAt?: string;
}

/** 数据字典类型（如 education / hire-type / resign-reason） */
export interface DictTypeResponse {
  id: number;
  code: string;
  name: string;
  updatedAt?: string;
}

/** 数据字典项 */
export interface DictItemResponse {
  id: number;
  typeCode: string;
  value: string;
  label: string;
  sort: number;
}

/** 审计日志（只追加，按租户隔离） */
export interface AuditLogResponse {
  id: number;
  tenantId: number;
  actorId?: number;
  action: string;
  resource: string;
  detail?: string;
  ip?: string;
  createdAt: string;
}

/** 通知模板（按渠道 + 编码唯一） */
export interface TemplateResponse {
  id: number;
  code: string;
  /** 渠道：INBOX / EMAIL / WEBHOOK */
  channel: string;
  subject?: string;
  content: string;
  updatedAt?: string;
}

/** 报表定义（defJson 为报表配置的 JSON 描述） */
export interface ReportDefResponse {
  id: number;
  code: string;
  name: string;
  defJson?: string;
  updatedAt?: string;
}
