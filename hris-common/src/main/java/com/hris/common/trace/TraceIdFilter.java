package com.hris.common.trace;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * traceId 透传过滤器（仅 Servlet Web 应用生效；WebFlux 网关单独实现）。
 * 入口：从 Header X-Trace-Id 取；无则生成 UUID。写入 TraceContext 与 MDC，
 * 响应体由 R 自动携带（见 com.hris.common.api.R）。
 */
@Component
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class TraceIdFilter extends OncePerRequestFilter {

    public static final String HEADER_TRACE_ID = "X-Trace-Id";
    public static final String HEADER_TENANT_ID = "X-Tenant-Id";
    public static final String HEADER_USER_ID = "X-User-Id";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String traceId = request.getHeader(HEADER_TRACE_ID);
        if (traceId == null || traceId.isBlank()) {
            traceId = UUID.randomUUID().toString().replace("-", "");
        }
        TraceContext.setTraceId(traceId);
        MDC.put("traceId", traceId);

        // 租户上下文仅取自网关校验后的内部透传头；公网直连时忽略
        String tenantId = request.getHeader(HEADER_TENANT_ID);
        if (tenantId != null && !tenantId.isBlank()) {
            try {
                TraceContext.setTenantId(Long.parseLong(tenantId));
            } catch (NumberFormatException ignore) {
                // 非法租户头忽略
            }
        }

        // 当前用户 ID（网关解析 JWT 后透传；公网直连时忽略，用户级接口需校验非空）
        String userId = request.getHeader(HEADER_USER_ID);
        if (userId != null && !userId.isBlank()) {
            try {
                TraceContext.setUserId(Long.parseLong(userId));
            } catch (NumberFormatException ignore) {
                // 非法用户头忽略
            }
        }

        try {
            response.setHeader(HEADER_TRACE_ID, traceId);
            filterChain.doFilter(request, response);
        } finally {
            TraceContext.clear();
            MDC.clear();
        }
    }
}
