package com.hris.flow.config;

import com.hris.common.trace.TraceContext;
import com.hris.common.trace.TraceIdFilter;
import feign.RequestInterceptor;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Feign 客户端扫描 + 内部调用头透传。
 *
 * <p>跨服务内部调用必须透传 X-Tenant-Id / X-Trace-Id，否则下游 TraceIdFilter 无法还原租户上下文
 * （与 hris-employee / hris-org 的 Feign 配置保持一致，docs/08 §2/§3）。</p>
 */
@Configuration
@EnableFeignClients(basePackages = "com.hris.flow.client")
public class FlowFeignConfig {

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
