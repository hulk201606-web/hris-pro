package com.hris.gateway.filter;

import com.hris.gateway.common.GatewayConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * 请求日志过滤器。置于 JWT 认证之前（外层），保证被拒绝（401/403/413/429）的请求也会记录。
 * method/path/traceId/tenantId/userId/status/耗时 均从交换属性与响应状态读取。
 */
@Component
public class AccessLogGlobalFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(AccessLogGlobalFilter.class);

    public static final int ORDER = -900;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        long start = System.currentTimeMillis();
        return chain.filter(exchange).doFinally(signalType -> {
            long costMs = System.currentTimeMillis() - start;
            ServerHttpRequest request = exchange.getRequest();
            HttpStatusCode status = exchange.getResponse().getStatusCode();
            String traceId = exchange.getAttribute(GatewayConstants.ATTR_TRACE_ID);
            Object tenantId = exchange.getAttribute(GatewayConstants.ATTR_TENANT_ID);
            Object userId = exchange.getAttribute(GatewayConstants.ATTR_USER_ID);
            log.info("access|method={}|path={}|status={}|traceId={}|tenantId={}|userId={}|costMs={}",
                    request.getMethod(),
                    request.getPath().value(),
                    status == null ? "-" : status.value(),
                    traceId,
                    tenantId,
                    userId,
                    costMs);
        });
    }

    @Override
    public int getOrder() {
        return ORDER;
    }
}
