/**
 * 官网免登录公开招聘 API（RCS-F-004）。
 *
 * 端点 /api/v1/public/** 在网关白名单匿名放行，因此本文件不携带 Authorization 头，
 * 也不走 http.ts 的 401 续期拦截（未登录场景无 token 可续）。
 * 复用统一响应体信封与 BizError 错误模型，保证页面侧错误处理口径一致。
 */
import axios from 'axios';
import type { AxiosRequestConfig } from 'axios';
import { BizError, resolveErrorCodeMessage, resolveHttpStatusMessage } from './error';
import type { ApiEnvelope } from '../types/api';

const BASE_URL = import.meta.env.VITE_API_BASE_URL || '/api/v1';

/** 官网按租户部署，tenantId 由站点配置注入（缺省 100001） */
export const PUBLIC_TENANT_ID = import.meta.env.VITE_PUBLIC_TENANT_ID || '100001';

/** 裸客户端：无请求拦截器，即不带 Authorization */
const publicClient = axios.create({ baseURL: BASE_URL, timeout: 15000 });

async function publicRequest<T>(config: AxiosRequestConfig): Promise<T> {
  let body: ApiEnvelope<T> | undefined;
  try {
    const resp = await publicClient.request<ApiEnvelope<T>>(config);
    body = resp.data;
  } catch (e) {
    // 网络/HTTP 层错误收敛为 BizError，文案口径与 http.ts 一致
    const status = (e as { response?: { status?: number; data?: ApiEnvelope } }).response?.status ?? 0;
    const respBody = (e as { response?: { data?: ApiEnvelope } }).response?.data;
    const code = respBody && typeof respBody.code === 'number' ? respBody.code : 0;
    const message = (respBody && respBody.message) || resolveHttpStatusMessage(status);
    throw new BizError(code || status, message, status, respBody?.traceId);
  }
  if (body && typeof body === 'object' && 'code' in body && body.code !== 0) {
    throw new BizError(
      body.code,
      body.message || resolveErrorCodeMessage(body.code),
      200,
      body.traceId,
    );
  }
  return (body && 'data' in body ? body.data : (body as unknown)) as T;
}

/** 公开职位项：OPEN + published=1 的需求单（非敏感字段，title 可能为 null） */
export interface PublicJobItem {
  reqId: number;
  reqNo: string;
  title: string | null;
  deptId: number;
  headcount: number;
  createdAt: string;
}

/** 投递受理结果 */
export interface PublicApplyResult {
  applicationId: number;
  message: string;
}

/** 公开职位列表（GET /public/rcu/jobs） */
export function listPublicJobs(): Promise<PublicJobItem[]> {
  return publicRequest<PublicJobItem[]>({
    method: 'GET',
    url: '/public/rcu/jobs',
    params: { tenantId: PUBLIC_TENANT_ID },
  });
}

/** 官网投递（POST /public/rcu/jobs/{reqId}/apply） */
export function applyPublicJob(
  reqId: number,
  data: { name: string; mobile: string; email: string; selfIntro?: string },
): Promise<PublicApplyResult> {
  return publicRequest<PublicApplyResult>({
    method: 'POST',
    url: `/public/rcu/jobs/${reqId}/apply`,
    params: { tenantId: PUBLIC_TENANT_ID },
    data,
  });
}

/** 投递失败码 → 页面友好文案（兜底仍走 BizError.message） */
export function resolveApplyFailText(code: number): string | null {
  if (code === 10002) return '该职位已关闭招聘，请查看其他职位';
  if (code === 10003) return '您已投递过该职位，请勿重复提交';
  if (code === 10001) return '填写信息有误，请检查后重试';
  if (code === 10429) return '投递过于频繁，请稍后再试';
  return null;
}
