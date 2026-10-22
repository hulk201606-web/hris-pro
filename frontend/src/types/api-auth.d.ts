/**
 * 前后端共享接口类型契约 —— 认证 / 用户 / 菜单模块
 */

export interface LoginRequest {
  tenantCode: string;
  username: string;
  password: string;
}

export interface RefreshRequest {
  refreshToken: string;
}

export interface TokenResponse {
  accessToken: string;
  refreshToken: string;
  tokenType: string;
  /** access 有效期（秒） */
  expiresIn: number;
  /** refresh 有效期（秒） */
  refreshExpiresIn: number;
}

export interface UserInfo {
  userId: number;
  employeeId: number;
  username: string;
  /** 1=正常 0=禁用 */
  status: number;
}

export interface RoleInfo {
  code: string;
  name: string;
}

export interface DataScopeInfo {
  roleCode: string;
  /** SELF / DEPT / DEPT_SUB / CUSTOM / ALL */
  scopeType: string;
  /** CUSTOM 时的部门 ID 集合（JSON 数组字符串） */
  deptIds: string;
}

export interface TenantBrand {
  tenantId: number;
  code: string;
  name: string;
  plan: string;
  timezone: string;
  domain: string;
}

export interface MeResponse {
  user: UserInfo;
  roles: RoleInfo[];
  /** 权限点（模块:资源:动作） */
  perms: string[];
  dataScopes: DataScopeInfo[];
  tenant: TenantBrand;
}

export interface MenuNode {
  code: string;
  name: string;
  /** module / menu / button */
  type: string;
  children?: MenuNode[];
}
