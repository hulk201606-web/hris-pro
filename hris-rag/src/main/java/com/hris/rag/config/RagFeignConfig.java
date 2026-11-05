package com.hris.rag.config;

import com.hris.common.trace.TraceContext;
import com.hris.common.trace.TraceIdFilter;
import feign.RequestInterceptor;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Feign 客户端扫描 + 内部调用头透传（知识库上下文：员工档案简报）。
 *
 * <p>跨服务内部调用必须透传 X-Tenant-Id / X-Trace-Id（docs/08 §2 服务间签名头 + §3 租户隔离）。</p>
 */
@Configuration
@EnableFeignClients(basePackages = {"com.hris.rag.client", "com.hris.common.client"})
public class RagFeignConfig {

    @Bean
    public RequestInterceptor ragTraceFeignInterceptor() {
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
