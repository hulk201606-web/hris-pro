package com.hris.employee.config;

import com.hris.common.security.AuthContextHolder;
import com.hris.common.trace.TraceContext;
import com.hris.common.trace.TraceIdFilter;
import feign.RequestInterceptor;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Feign 客户端扫描 + 内部调用头透传。
 *
 * <p>跨服务内部调用必须透传 X-Tenant-Id / X-Trace-Id（docs/08 §2 服务间签名头 + §3 租户隔离），
 * 否则下游 TraceIdFilter 无法还原租户上下文，租户插件会注入 tenant_id=0 导致跨租户风险。</p>
 */
@Configuration
@EnableFeignClients(basePackages = {"com.hris.employee.client", "com.hris.common.client"})
public class EmployeeFeignConfig {

    @Bean
    public RequestInterceptor traceFeignInterceptor() {
        return template -> {
            String traceId = TraceContext.getTraceId();
            Long tenantId = TraceContext.getTenantId();
            if (traceId != null && !traceId.isBlank()) {
                template.header(TraceIdFilter.HEADER_TRACE_ID, traceId);
            }
            if (tenantId != null) {
                template.header(TraceIdFilter.HEADER_TENANT_ID, String.valueOf(tenantId));
            }
            // 自助聚合/跨域查询要代用户取数：透传用户与权限头，否则下游 /me 与 @RequirePerm 全部 401/403
            Long userId = AuthContextHolder.getUserId() != null
                    ? AuthContextHolder.getUserId() : TraceContext.getUserId();
            if (userId != null) {
                template.header(com.hris.common.security.AuthContextFilter.HEADER_USER_ID,
                        String.valueOf(userId));
            }
            java.util.Set<String> perms = AuthContextHolder.getPerms();
            if (perms != null && !perms.isEmpty()) {
                template.header(com.hris.common.security.AuthContextFilter.HEADER_PERMS,
                        String.join(",", perms));
            }
            java.util.Set<String> roles = AuthContextHolder.getRoles();
            if (roles != null && !roles.isEmpty()) {
                template.header(com.hris.common.security.AuthContextFilter.HEADER_ROLES,
                        String.join(",", roles));
            }
        };
    }
}
