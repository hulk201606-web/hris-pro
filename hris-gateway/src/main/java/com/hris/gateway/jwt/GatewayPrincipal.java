package com.hris.gateway.jwt;

/**
 * 网关解析 JWT 后得到的主体信息（透传下游所需的最小字段）。
 *
 * <p>roles/perms 不再进 JWT（权限由网关验签后经 PermQueryService 实时查询 auth 内部端点），
 * 故主体仅保留身份相关字段。</p>
 *
 * @param userId   JWT subject（auth 服务签发的用户 ID）
 * @param tenantId JWT tenant_id（租户上下文唯一可信来源）
 * @param empId    JWT emp_id（可能为空，如平台级账号）
 */
/**
 * 网关侧已验签主体。jti 用于登出/禁用黑名单校验（auth:bl:{jti}，与 auth RedisTokenStore 键约定一致）。
 */
public record GatewayPrincipal(Long userId, Long tenantId, Long empId, String jti) {
}
