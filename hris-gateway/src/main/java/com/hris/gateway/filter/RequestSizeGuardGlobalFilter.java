package com.hris.gateway.filter;

import com.hris.common.exception.ErrorCode;
import com.hris.gateway.config.HrisGatewayProperties;
import com.hris.gateway.handler.GatewayErrorWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * 请求体大小守卫。Content-Length 超过阈值直接 413，避免大报文打满编解码内存。
 */
@Component
public class RequestSizeGuardGlobalFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(RequestSizeGuardGlobalFilter.class);

    public static final int ORDER = -850;

    private final HrisGatewayProperties props;
    private final GatewayErrorWriter errorWriter;

    public RequestSizeGuardGlobalFilter(HrisGatewayProperties props, GatewayErrorWriter errorWriter) {
        this.props = props;
        this.errorWriter = errorWriter;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String contentLength = exchange.getRequest().getHeaders().getFirst("Content-Length");
        if (contentLength != null && !contentLength.isBlank()) {
            try {
                long length = Long.parseLong(contentLength.trim());
                if (length > props.getMaxBodySizeBytes()) {
                    log.warn("request body too large, path={}, length={}",
                            exchange.getRequest().getPath().value(), length);
                    return errorWriter.write(exchange, HttpStatus.PAYLOAD_TOO_LARGE, ErrorCode.SYSTEM_ERROR);
                }
            } catch (NumberFormatException ignore) {
                // Content-Length 非法时交给下游框架处理，不在此处拦截
            }
        }
        return chain.filter(exchange);
    }

    @Override
    public int getOrder() {
        return ORDER;
    }
}
