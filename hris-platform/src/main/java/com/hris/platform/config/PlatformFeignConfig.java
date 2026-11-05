package com.hris.platform.config;

import com.hris.common.trace.TraceContext;
import com.hris.common.trace.TraceIdFilter;
import feign.RequestInterceptor;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Feign 客户端扫描 + 内部调用头透传（platform 跨域调度任务用，docs/19 §9.1）。
 *
 * <p>调度线程的租户上下文由 SchedulerRegistry 注入，经本拦截器透传
 * X-Tenant-Id / X-Trace-Id 给下游（employee 等），否则下游租户插件注入
 * tenant_id=0 造成跨租户风险。</p>
 */
@Configuration
@EnableFeignClients(basePackages = {"com.hris.platform.client", "com.hris.common.client"})
public class PlatformFeignConfig {

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
        };
    }
}
