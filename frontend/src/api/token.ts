/**
 * 令牌存取与续期协调（独立于 Pinia，避免循环依赖）。
 * 存储键前缀按租户应用隔离；多租户令牌由登录返回的 JWT 承载，客户端仅透传。
 */

const ACCESS_KEY = 'hris.access_token';
const REFRESH_KEY = 'hris.refresh_token';

export function getAccessToken(): string | null {
  return localStorage.getItem(ACCESS_KEY);
}

export function getRefreshToken(): string | null {
  return localStorage.getItem(REFRESH_KEY);
}

export function setTokens(access: string, refresh: string): void {
  localStorage.setItem(ACCESS_KEY, access);
  localStorage.setItem(REFRESH_KEY, refresh);
}

export function clearTokens(): void {
  localStorage.removeItem(ACCESS_KEY);
  localStorage.removeItem(REFRESH_KEY);
}

/** 是否已登录（仅作路由守卫的快速判断，不校验令牌有效性） */
export function hasAccessToken(): boolean {
  return Boolean(getAccessToken());
}
