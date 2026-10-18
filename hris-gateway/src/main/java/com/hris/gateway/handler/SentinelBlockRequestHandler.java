package com.hris.gateway.handler;

import com.alibaba.csp.sentinel.adapter.spring.webflux.callback.BlockRequestHandler;
import com.hris.common.exception.ErrorCode;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerResponse;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Sentinel 限流被触发时的响应处理（R 格式，code=10429）。
 * SCA 的 SentinelWebFluxAutoConfiguration 检测到本 Bean 后，
 * 会自动注册为 WebFluxCallbackManager 的 block handler。
 */
@Component
public class SentinelBlockRequestHandler implements BlockRequestHandler {

    private final GatewayErrorWriter errorWriter;

    public SentinelBlockRequestHandler(GatewayErrorWriter errorWriter) {
        this.errorWriter = errorWriter;
    }

    @Override
    public Mono<ServerResponse> handleRequest(ServerWebExchange exchange, Throwable ex) {
        return ServerResponse.status(HttpStatus.TOO_MANY_REQUESTS)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(errorWriter.build(exchange, ErrorCode.RATE_LIMITED));
    }
}
