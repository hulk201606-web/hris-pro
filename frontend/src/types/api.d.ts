/**
 * 前后端共享接口类型契约（对齐 docs/openapi.yaml）
 * 统一响应体：{ code, message, data, traceId, timestamp }；分页 data = { records, page, size, total }
 *
 * 说明：类型按模块拆分到同目录各文件，本文件仅做统一再导出，
 * 外部仍按 `@/types/api` 引入即可，无需改动导入路径。
 */

export * from './api-common';
export * from './api-auth';
export * from './api-employee';
export * from './api-flow';
export * from './api-org';
export * from './api-platform';
export * from './api-recruit';
export * from './api-self';
