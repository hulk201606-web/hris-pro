package com.hris.common.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 权限上下文过滤器（仅 Servlet 应用生效）。从网关校验后透传的内部头还原
 * AuthContextHolder（userId/tenantId/perms/roles），供 @RequirePerm 切面做权限判定。
 *
 * <p>这些头由网关 JwtAuthGlobalFilter 验签后写入，公网不可伪造。缺失时 perms 为空集合，
 * 受保护端点（@RequirePerm）会被拦截。</p>
 */
@Component
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class AuthContextFilter extends OncePerRequestFilter {

    public static final String HEADER_USER_ID = "X-User-Id";
    public static final String HEADER_TENANT_ID = "X-Tenant-Id";
    public static final String HEADER_PERMS = "X-User-Perms";
    public static final String HEADER_ROLES = "X-User-Roles";

    /**
     * 从网关透传的内部头还原权限上下文：X-User-Id / X-Tenant-Id / X-User-Perms / X-User-Roles。
     * 这些头由网关 JwtAuthGlobalFilter 验签后写入，公网请求无法伪造；缺失时 perms/roles 为空集合，
     * 受 @RequirePerm 保护的端点会被 PermAspect 以 20403 拒绝。
     * finally 块清理 ThreadLocal，防止线程复用导致上下文串号。
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        AuthContextHolder.setUserId(parseLong(request.getHeader(HEADER_USER_ID)));
        AuthContextHolder.setTenantId(parseLong(request.getHeader(HEADER_TENANT_ID)));
        AuthContextHolder.setPerms(parseCsv(request.getHeader(HEADER_PERMS)));
        AuthContextHolder.setRoles(parseCsv(request.getHeader(HEADER_ROLES)));
        try {
            filterChain.doFilter(request, response);
        } finally {
            AuthContextHolder.clear();
        }
    }

    private Long parseLong(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException ignore) {
            return null;
        }
    }

    private Set<String> parseCsv(String value) {
        if (value == null || value.isBlank()) {
            return Set.of();
        }
        return Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toUnmodifiableSet());
    }
}
