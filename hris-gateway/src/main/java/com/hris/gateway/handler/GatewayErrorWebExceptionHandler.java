package com.hris.gateway.handler;

import com.alibaba.csp.sentinel.slots.block.BlockException;
import com.hris.common.exception.ErrorCode;
import org.springframework.boot.webflux.error.ErrorWebExceptionHandler;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.ErrorResponse;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.ConnectException;
import java.util.concurrent.TimeoutException;

/**
 * 网关全局异常处理器（WebFlux）。替换 Boot 默认的 DefaultErrorWebExceptionHandler，
 * 将路由不存在(404)、下游连接失败(503)、超时(504) 及未知异常(500) 收敛为统一 R 响应体。
 *
 * <p>Sentinel 的 {@link BlockException} 交由 SCA 的 SentinelBlockExceptionHandler 处理
 * （以 Mono.error 重新抛出让其接管），避免两处重复写限流响应。</p>
 */
@Component
@Order(-1)
public class GatewayErrorWebExceptionHandler implements ErrorWebExceptionHandler {

    private final GatewayErrorWriter errorWriter;

    public GatewayErrorWebExceptionHandler(GatewayErrorWriter errorWriter) {
        this.errorWriter = errorWriter;
    }

    /**
     * 网关全局异常入口（@Order(-1)，优先于 Boot 默认处理器）：
     * Sentinel BlockException 重新抛出交由 SCA 限流处理器接管；其余异常按类型解析 HTTP 状态码，
     * 再映射为 ErrorCode 枚举，通过 GatewayErrorWriter 写出统一 R 响应体。
     */
    @Override
    public Mono<Void> handle(ServerWebExchange exchange, Throwable ex) {
        // 限流类 BlockException 交由 SCA 的 SentinelBlockExceptionHandler 处理：
        // WebExceptionHandler 链中"拒绝处理"必须返回 Mono.error 重新抛出，
        // 返回 Mono.empty 会把异常吞掉导致响应不落盘。
        if (BlockException.isBlockException(ex)) {
            return Mono.error(ex);
        }
        HttpStatus status = resolveStatus(ex);
        return errorWriter.write(exchange, status, resolveCode(status));
    }

    /**
     * 按异常类型解析 HTTP 状态码：ErrorResponse（含 NoResourceFoundException/ResponseStatusException）
     * 取其声明状态；ConnectException → 503；TimeoutException → 504；其余 → 500。
     * 关键：先取 ErrorResponse 可避免把"路径不存在"误判为 500 系统错误。
     */
    private HttpStatus resolveStatus(Throwable ex) {
        // 先按 ErrorResponse 取状态码：ResponseStatusException 与「无 handler / 无路由」抛出的
        // NoResourceFoundException 都实现了它。不作这层判断时，访问不存在的路径会被
        // default 分支吞成 500「系统内部错误」，与 Servlet 侧历史缺陷同源
        // （Servlet 侧已由 NotFoundExceptionHandler 还原 404，此处是 WebFlux 侧的对称修复）。
        if (ex instanceof ErrorResponse er) {
            HttpStatus status = HttpStatus.resolve(er.getStatusCode().value());
            if (status != null) {
                return status;
            }
        }
        if (ex instanceof ResponseStatusException rse) {
            HttpStatus status = HttpStatus.resolve(rse.getStatusCode().value());
            return status != null ? status : HttpStatus.INTERNAL_SERVER_ERROR;
        }
        if (ex instanceof ConnectException) {
            return HttpStatus.SERVICE_UNAVAILABLE;
        }
        if (ex instanceof TimeoutException) {
            return HttpStatus.GATEWAY_TIMEOUT;
        }
        return HttpStatus.INTERNAL_SERVER_ERROR;
    }

    /**
     * HTTP 状态码 → 业务错误码映射：404→NOT_FOUND、401→UNAUTHORIZED、403→FORBIDDEN、
     * 429→RATE_LIMITED，其余 → SYSTEM_ERROR。与 Servlet 侧 GlobalExceptionHandler 语义对齐。
     */
    private ErrorCode resolveCode(HttpStatus status) {
        return switch (status.value()) {
            case 404 -> ErrorCode.NOT_FOUND;
            case 401 -> ErrorCode.UNAUTHORIZED;
            case 403 -> ErrorCode.FORBIDDEN;
            case 429 -> ErrorCode.RATE_LIMITED;
            default -> ErrorCode.SYSTEM_ERROR;
        };
    }
}
