import { defineStore } from 'pinia';
import { login as loginApi, fetchMe, fetchMenus, logout as logoutApi } from '../api/auth';
import { clearTokens, getAccessToken, getRefreshToken, setTokens } from '../api/token';
import type { LoginRequest, MenuNode, RoleInfo, TenantBrand, UserInfo } from '../types/api';

interface UserState {
  accessToken: string | null;
  refreshToken: string | null;
  userInfo: UserInfo | null;
  roles: RoleInfo[];
  perms: string[];
  tenant: TenantBrand | null;
  menus: MenuNode[];
  loaded: boolean;
}

/** 登录态 / 令牌 / 用户信息 / 权限点缓存（单一用户会话 store） */
export const useUserStore = defineStore('user', {
  state: (): UserState => ({
    accessToken: getAccessToken(),
    refreshToken: getRefreshToken(),
    userInfo: null,
    roles: [],
    perms: [],
    tenant: null,
    menus: [],
    loaded: false,
  }),

  getters: {
    isAuthenticated: (s): boolean => Boolean(s.accessToken),
    displayName: (s): string => s.userInfo?.username ?? '',
    employeeId: (s): number => s.userInfo?.employeeId ?? 0,
    tenantName: (s): string => s.tenant?.name ?? '',
    /** 按钮级权限判断（权限点编码 模块:资源:动作） */
    hasPerm: (s) => (perm: string): boolean => s.perms.includes(perm),
  },

  actions: {
    async login(form: LoginRequest) {
      const token = await loginApi(form);
      setTokens(token.accessToken, token.refreshToken);
      this.accessToken = token.accessToken;
      this.refreshToken = token.refreshToken;
    },

    /** 拉取当前用户 + 权限摘要 + 租户品牌 */
    async loadProfile() {
      const me = await fetchMe();
      this.userInfo = me.user;
      this.roles = me.roles ?? [];
      this.perms = me.perms ?? [];
      this.tenant = me.tenant ?? null;
      this.loaded = true;
      return me;
    },

    /** 菜单树（接口驱动预留：GET /auth/menus） */
    async loadMenus() {
      this.menus = await fetchMenus();
    },

    async logout() {
      try {
        await logoutApi();
      } finally {
        this.reset();
      }
    },

    reset() {
      clearTokens();
      this.accessToken = null;
      this.refreshToken = null;
      this.userInfo = null;
      this.roles = [];
      this.perms = [];
      this.tenant = null;
      this.menus = [];
      this.loaded = false;
    },
  },
});
