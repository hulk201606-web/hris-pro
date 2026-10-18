package com.hris.common.security;

import java.util.Set;

/**
 * 下游服务权限上下文（ThreadLocal）。由 {@link AuthContextFilter} 从网关透传头还原，
 * 供 {@link PermAspect} 做垂直越权拦截。
 *
 * <p>注：当前用 ThreadLocal，适用于同步调用链；异步线程池 / 消息消费场景
 * 需替换为 TransmittableThreadLocal（与 TraceContext 的演进一致）。</p>
 */
public final class AuthContextHolder {

    private static final ThreadLocal<Long> USER_ID = new ThreadLocal<>();
    private static final ThreadLocal<Long> TENANT_ID = new ThreadLocal<>();
    private static final ThreadLocal<Set<String>> PERMS = new ThreadLocal<>();
    private static final ThreadLocal<Set<String>> ROLES = new ThreadLocal<>();

    private AuthContextHolder() {
    }

    public static void setUserId(Long userId) {
        USER_ID.set(userId);
    }

    public static Long getUserId() {
        return USER_ID.get();
    }

    public static void setTenantId(Long tenantId) {
        TENANT_ID.set(tenantId);
    }

    public static Long getTenantId() {
        return TENANT_ID.get();
    }

    public static void setPerms(Set<String> perms) {
        PERMS.set(perms);
    }

    public static Set<String> getPerms() {
        return PERMS.get();
    }

    public static void setRoles(Set<String> roles) {
        ROLES.set(roles);
    }

    public static Set<String> getRoles() {
        return ROLES.get();
    }

    public static void clear() {
        USER_ID.remove();
        TENANT_ID.remove();
        PERMS.remove();
        ROLES.remove();
    }
}
