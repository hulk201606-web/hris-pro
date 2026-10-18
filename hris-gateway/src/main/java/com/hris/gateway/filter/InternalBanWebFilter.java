package com.hris.gateway.filter;

import com.hris.common.exception.ErrorCode;
import com.hris.gateway.config.HrisGatewayProperties;
import com.hris.gateway.handler.GatewayErrorWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

/**
 * 内部端点封禁（WebFilter 层）。
 *
 * <p>关键：/internal/** 不匹配任何 Gateway 路由，GlobalFilter 链不会执行，
 * 请求会 fallback 到 WebFlux 静态资源处理器抛 NoResourceFoundException。
 * 因此内部端点封禁必须在 WebFilter 层（先于路由匹配）拦截，而非 GlobalFilter。</p>
 *
 * <p>内部端点仅允许服务间 Feign 直连，不暴露经网关公网打探（docs/08 §4 越权矩阵）。</p>
 */
@Component
public class InternalBanWebFilter implements WebFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(InternalBanWebFilter.class);

    /** 顺序：早于 TraceIdGlobalFilter 之后的业务过滤，但晚于 WebFlux 框架默认处理。 */
    public static final int ORDER = -700;

    private final HrisGatewayProperties props;
    private final GatewayErrorWriter errorWriter;

    public InternalBanWebFilter(HrisGatewayProperties props, GatewayErrorWriter errorWriter) {
        this.props = props;
        this.errorWriter = errorWriter;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String path = exchange.getRequest().getURI().getPath();
        if (path.contains(props.getInternalPathMarker())) {
            log.warn("internal endpoint access blocked via gateway (WebFilter), path={}", path);
            return errorWriter.write(exchange, HttpStatus.FORBIDDEN, ErrorCode.FORBIDDEN);
        }
        return chain.filter(exchange);
    }

    @Override
    public int getOrder() {
        return ORDER;
    }
}
