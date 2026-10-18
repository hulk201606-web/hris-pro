package com.hris.gateway.common;

/**
 * 网关过滤器链共享常量：请求/响应头、Reactor 交换属性键、JWT claims 字段名。
 *
 * <p>JWT claims 字段名与 hris-auth 的 TokenService 保持一致（snake_case），
 * 不得臆造 camelCase 别名。</p>
 */
public final class GatewayConstants {

    private GatewayConstants() {
    }

    /* ---------- 请求/响应头 ---------- */
    public static final String HEADER_TRACE_ID = "X-Trace-Id";
    public static final String HEADER_USER_ID = "X-User-Id";
    public static final String HEADER_TENANT_ID = "X-Tenant-Id";
    public static final String HEADER_PERMS = "X-User-Perms";
    public static final String HEADER_ROLES = "X-User-Roles";
    public static final String HEADER_AUTHORIZATION = "Authorization";
    public static final String BEARER_PREFIX = "Bearer ";

    /* ---------- ServerWebExchange 属性键（GlobalFilter 间数据交换的可靠通道） ---------- */
    public static final String ATTR_TRACE_ID = "gateway.traceId";
    public static final String ATTR_USER_ID = "gateway.userId";
    public static final String ATTR_TENANT_ID = "gateway.tenantId";
    public static final String ATTR_SKIP_AUTH = "gateway.skipAuth";

    /* ---------- JWT claims（与 hris-auth TokenService 对齐） ---------- */
    public static final String CLAIM_TENANT_ID = "tenant_id";
    public static final String CLAIM_EMP_ID = "emp_id";
    public static final String CLAIM_ROLES = "roles";
    public static final String CLAIM_PERMS = "perms";
    public static final String CLAIM_TYPE = "typ";
    public static final String TOKEN_TYPE_ACCESS = "access";
}
