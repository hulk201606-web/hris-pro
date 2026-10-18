/**
 * 业务错误模型 + 错误码 → 中文文案映射（对齐 docs/07 §2 分段码）。
 */

/** 业务异常：后端返回 code !== 0 或网络/HTTP 异常统一收敛为此类型 */
export class BizError extends Error {
  readonly code: number;
  readonly traceId?: string;
  /** HTTP 状态码（无则为 0） */
  readonly status: number;

  constructor(code: number, message: string, status = 0, traceId?: string) {
    super(message);
    this.name = 'BizError';
    this.code = code;
    this.status = status;
    this.traceId = traceId;
  }
}

/** 错误码分段 → 中文文案（后端 message 缺失时的兜底） */
export function resolveErrorCodeMessage(code: number): string {
  if (code === 0) return '操作成功';

  // 10000-10999 通用
  if (code >= 10000 && code < 11000) {
    if (code === 10429) return '请求过于频繁，请稍后再试';
    return '请求参数有误，请检查后重试';
  }

  // 20000-29999 认证授权
  if (code >= 20000 && code < 30000) {
    if (code === 20401) return '登录状态已失效，请重新登录';
    if (code === 20403) return '无权访问该资源';
    if (code === 20410) return '该操作需要二次认证';
    if (code === 20501) return '当前租户已失效，请联系管理员';
    return '认证失败，请重新登录';
  }

  // 30000-39999 流程
  if (code >= 30000 && code < 40000) {
    if (code === 30404) return '流程不存在或已被删除';
    if (code === 30409) return '流程状态已变更，请刷新后重试';
    if (code === 30423) return '您无权处理该任务';
    return '流程处理失败，请稍后重试';
  }

  // 40000-49999 RAG
  if (code >= 40000 && code < 50000) {
    if (code === 40050) return '智能问答服务暂时不可用，请稍后再试';
    if (code === 40060) return '该问题暂无依据，无法作答';
    return '智能服务暂时不可用';
  }

  // 5xxxx 业务域（组织 50 / 员工 51 / 假勤 52 / 招聘 53 / 薪酬 54 / 绩效 55）
  if (code >= 50000 && code < 60000) {
    if (code === 51204) return '编制不足，无法完成该操作';
    if (code === 54001) return '薪酬为密域数据，您暂无查看权限';
    return '业务处理失败';
  }

  // 50000+ 系统内部错误（不泄露堆栈）
  return '系统繁忙，请稍后重试';
}

/** HTTP 状态码 → 中文文案 */
export function resolveHttpStatusMessage(status: number): string {
  switch (status) {
    case 400:
      return '请求有误';
    case 401:
      return '登录状态已失效';
    case 403:
      return '无权访问该资源';
    case 404:
      return '请求的资源不存在';
    case 409:
      return '数据冲突，请刷新后重试';
    case 429:
      return '请求过于频繁';
    case 500:
    case 502:
    case 503:
    case 504:
      return '服务暂时不可用，请稍后重试';
    default:
      return `请求失败（${status}）`;
  }
}
