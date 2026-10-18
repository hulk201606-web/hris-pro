package com.hris.gateway.handler;

import com.hris.common.api.R;
import com.hris.common.exception.ErrorCode;
import com.hris.gateway.common.GatewayConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import tools.jackson.databind.ObjectMapper;

import java.util.UUID;

/**
 * 网关统一错误响应写出器。复用 hris-common 的 R 响应体与 ErrorCode，
 * 组装 {code, message, data, traceId, timestamp} JSON。
 *
 * <p>注意：WebFlux 下 hris-common 的 TraceContext 是 ThreadLocal，跨响应式线程不可靠，
 * 故 traceId 在此处显式解析并注入 R，不依赖 TraceContext。</p>
 */
@Component
public class GatewayErrorWriter {

    private static final Logger log = LoggerFactory.getLogger(GatewayErrorWriter.class);

    private final ObjectMapper objectMapper;

    public GatewayErrorWriter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /** 构建统一响应体（data 恒为 null，供过滤器与 Sentinel 限流处理器复用）。 */
    public R<Void> build(ServerWebExchange exchange, ErrorCode errorCode) {
        R<Void> r = new R<>();
        r.setCode(errorCode.getCode());
        r.setMessage(errorCode.getMessage());
        r.setTraceId(resolveTraceId(exchange));
        r.setTimestamp(System.currentTimeMillis());
        return r;
    }

    /** 直接写出错误响应（用于 GlobalFilter 短路拒绝）。 */
    public Mono<Void> write(ServerWebExchange exchange, HttpStatus status, ErrorCode errorCode) {
        ServerHttpResponse response = exchange.getResponse();
        if (response.isCommitted()) {
            return response.setComplete();
        }
        response.setStatusCode(status);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        try {
            byte[] bytes = objectMapper.writeValueAsBytes(build(exchange, errorCode));
            DataBuffer buffer = response.bufferFactory().wrap(bytes);
            return response.writeWith(Mono.just(buffer));
        } catch (Exception e) {
            log.error("write gateway error response failed, traceId={}", resolveTraceId(exchange), e);
            return response.setComplete();
        }
    }

    private String resolveTraceId(ServerWebExchange exchange) {
        Object attr = exchange.getAttribute(GatewayConstants.ATTR_TRACE_ID);
        if (attr != null) {
            return attr.toString();
        }
        String header = exchange.getRequest().getHeaders().getFirst(GatewayConstants.HEADER_TRACE_ID);
        if (header != null && !header.isBlank()) {
            return header;
        }
        return UUID.randomUUID().toString().replace("-", "");
    }
}
