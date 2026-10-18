import axios from 'axios';
import type { AxiosError, AxiosInstance, AxiosRequestConfig, InternalAxiosRequestConfig } from 'axios';
import { BizError, resolveErrorCodeMessage, resolveHttpStatusMessage } from './error';
import { clearTokens, getAccessToken, getRefreshToken, setTokens } from './token';
import type { ApiEnvelope, TokenResponse } from '../types/api';

const BASE_URL = import.meta.env.VITE_API_BASE_URL || '/api/v1';

/** 裸客户端：登录/刷新等不应触发业务拦截与续期的请求 */
const rawClient = axios.create({ baseURL: BASE_URL, timeout: 15000 });

/** 401 静默续期协调：并发请求共享同一次 refresh */
let refreshPromise: Promise<string | null> | null = null;

function isEnvelope(data: unknown): data is ApiEnvelope {
  return typeof data === 'object' && data !== null && 'code' in data;
}

/** 从响应体/响应头提取 traceId 用于排障透传 */
function extractTraceId(headers: unknown, body: unknown): string | undefined {
  const headerTrace =
    headers && typeof headers === 'object' && 'x-trace-id' in headers
      ? (headers as Record<string, unknown>)['x-trace-id']
      : undefined;
  if (typeof headerTrace === 'string' && headerTrace) return headerTrace;
  if (body && typeof body === 'object' && 'traceId' in body) {
    const t = (body as { traceId?: unknown }).traceId;
    if (typeof t === 'string' && t) return t;
  }
  return undefined;
}

async function refreshAccessToken(): Promise<string | null> {
  const refreshToken = getRefreshToken();
  if (!refreshToken) return null;
  try {
    const resp = await rawClient.post<ApiEnvelope<TokenResponse>>('/auth/refresh', { refreshToken });
    const body = resp.data;
    if (body.code !== 0 || !body.data) return null;
    setTokens(body.data.accessToken, body.data.refreshToken);
    return body.data.accessToken;
  } catch {
    return null;
  }
}

export const http: AxiosInstance = axios.create({
  baseURL: BASE_URL,
  timeout: 15000,
});

http.interceptors.request.use((config) => {
  const token = getAccessToken();
  if (token) {
    config.headers = config.headers ?? {};
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

http.interceptors.response.use(
  (response) => {
    const body = response.data;
    if (isEnvelope(body) && body.code !== 0) {
      throw new BizError(
        body.code,
        body.message || resolveErrorCodeMessage(body.code),
        response.status,
        body.traceId,
      );
    }
    return response;
  },
  async (error: AxiosError) => {
    const status = error.response?.status ?? 0;
    const traceId = extractTraceId(error.response?.headers, error.response?.data);
    const original = error.config as (InternalAxiosRequestConfig & { _retried?: boolean }) | undefined;

    // 401 静默续期（仅重试一次）
    if (status === 401 && original && !original._retried) {
      original._retried = true;
      refreshPromise = refreshPromise ?? refreshAccessToken();
      const newToken = await refreshPromise;
      refreshPromise = null;
      if (newToken) {
        original.headers = original.headers ?? {};
        original.headers.Authorization = `Bearer ${newToken}`;
        return http.request(original);
      }
      clearTokens();
      if (typeof window !== 'undefined' && window.location.pathname !== '/login') {
        window.location.href = '/login';
      }
      throw new BizError(20401, '登录状态已失效，请重新登录', status, traceId);
    }

    if (error.response) {
      const body = error.response.data as ApiEnvelope | undefined;
      const code = body && typeof body.code === 'number' ? body.code : 0;
      const message = (body && body.message) || resolveHttpStatusMessage(status);
      throw new BizError(code || status, message, status, traceId ?? body?.traceId);
    }

    if (error.code === 'ECONNABORTED') {
      throw new BizError(0, '请求超时，请稍后重试', status, traceId);
    }
    throw new BizError(0, '网络异常，请检查网络连接', status, traceId);
  },
);

/** 泛型请求：自动解包统一响应体 data */
export async function request<T>(config: AxiosRequestConfig): Promise<T> {
  const resp = await http.request<ApiEnvelope<T>>(config);
  const body = resp.data;
  return isEnvelope(body) ? (body.data as T) : (body as unknown as T);
}

export function get<T>(url: string, params?: Record<string, unknown>): Promise<T> {
  return request<T>({ method: 'GET', url, params });
}

export function post<T>(url: string, data?: unknown): Promise<T> {
  return request<T>({ method: 'POST', url, data });
}

export function put<T>(url: string, data?: unknown): Promise<T> {
  return request<T>({ method: 'PUT', url, data });
}

export function del<T>(url: string): Promise<T> {
  return request<T>({ method: 'DELETE', url });
}
