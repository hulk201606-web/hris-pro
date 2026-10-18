package com.hris.gateway.filter;

import com.hris.gateway.common.GatewayConstants;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * 入口 traceId 过滤器（最外层）。无 X-Trace-Id 则生成 UUID（去横线），
 * 写入交换属性 + 响应头 + Reactor Context，供后续过滤器、错误处理与日志复用。
 */
@Component
public class TraceIdGlobalFilter implements GlobalFilter, Ordered {

    public static final int ORDER = -1000;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String incoming = exchange.getRequest().getHeaders().getFirst(GatewayConstants.HEADER_TRACE_ID);
        final String traceId = (incoming == null || incoming.isBlank())
                ? UUID.randomUUID().toString().replace("-", "")
                : incoming;
        exchange.getAttributes().put(GatewayConstants.ATTR_TRACE_ID, traceId);
        exchange.getResponse().getHeaders().set(GatewayConstants.HEADER_TRACE_ID, traceId);
        return chain.filter(exchange)
                .contextWrite(ctx -> ctx.put(GatewayConstants.ATTR_TRACE_ID, traceId));
    }

    @Override
    public int getOrder() {
        return ORDER;
    }
}
