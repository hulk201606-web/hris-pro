/**
 * 前后端共享接口类型契约（对齐 docs/openapi.yaml）—— 通用信封与分页结构
 * 统一响应体：{ code, message, data, traceId, timestamp }；分页 data = { records, page, size, total }
 */

/** 统一响应信封 */
export interface ApiEnvelope<T = unknown> {
  /** 0=成功，非 0=错误（分段见 docs/07 §2） */
  code: number;
  message: string;
  data: T;
  traceId: string;
  timestamp: number;
}

/** 分页响应 */
export interface PageResult<T> {
  records: T[];
  page: number;
  size: number;
  total: number;
}
