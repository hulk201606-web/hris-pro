package com.hris.recruit.config;

import com.hris.common.trace.TraceContext;
import com.hris.common.trace.TraceIdFilter;
import feign.RequestInterceptor;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Feign 客户端扫描 + 内部调用头透传（录用建档：员工域预建档）。
 *
 * <p>跨服务内部调用必须透传 X-Tenant-Id / X-Trace-Id（docs/08 §2 服务间签名头 + §3 租户隔离），
 * 否则下游 TraceIdFilter 无法还原租户上下文，租户插件会注入 tenant_id=0 导致跨租户风险。</p>
 */
@Configuration
@EnableFeignClients(basePackages = "com.hris.recruit.client")
public class RecruitFeignConfig {

    @Bean
    public RequestInterceptor recruitTraceFeignInterceptor() {
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
