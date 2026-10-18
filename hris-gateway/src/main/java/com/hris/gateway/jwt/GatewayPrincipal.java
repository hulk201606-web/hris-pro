package com.hris.gateway.jwt;

import java.util.List;

/**
 * 网关解析 JWT 后得到的主体信息（透传下游所需的最小字段）。
 *
 * @param userId   JWT subject（auth 服务签发的用户 ID）
 * @param tenantId JWT tenant_id（租户上下文唯一可信来源）
 * @param empId    JWT emp_id（可能为空，如平台级账号）
 * @param roles    JWT roles（角色编码列表，供下游 RBAC）
 * @param perms    JWT perms（权限点列表，模块:资源:动作，供下游 RBAC）
 */
public record GatewayPrincipal(Long userId, Long tenantId, Long empId,
                               List<String> roles, List<String> perms) {
}
