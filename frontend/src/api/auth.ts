import { get, post } from './http';
import { isMockEnabled, mockApi } from './mock';
import type { LoginRequest, MenuNode, MeResponse, TokenResponse } from '../types/api';

/** 账号密码登录（POST /auth/login） */
export function login(req: LoginRequest): Promise<TokenResponse> {
  if (isMockEnabled()) return mockApi.login(req);
  return post<TokenResponse>('/auth/login', req);
}

/** 当前用户 + 权限摘要 + 租户品牌（GET /auth/me） */
export function fetchMe(): Promise<MeResponse> {
  if (isMockEnabled()) return mockApi.me();
  return get<MeResponse>('/auth/me');
}

/** 当前用户菜单树（GET /auth/menus，菜单接口驱动预留） */
export function fetchMenus(): Promise<MenuNode[]> {
  if (isMockEnabled()) return mockApi.menus();
  return get<MenuNode[]>('/auth/menus');
}

/** 登出（POST /auth/logout） */
export function logout(): Promise<void> {
  if (isMockEnabled()) return Promise.resolve();
  return post<void>('/auth/logout');
}
