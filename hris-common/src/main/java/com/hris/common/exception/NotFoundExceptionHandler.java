package com.hris.common.exception;

import com.hris.common.api.R;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * 未匹配路径处理器：把「路径不存在」还原成 404，而不是被兜底处理器吞成 500。
 *
 * <p>背景：Spring Framework 6.1+ 对没有 handler 的 GET 请求会抛
 * {@link NoResourceFoundException}。它会被
 * {@link GlobalExceptionHandler} 的 {@code Exception} 兜底分支捕获，返回 50001
 * 「系统内部错误」——于是扫描不存在的路径、路径穿越探测，全都拿到 500，
 * 既误导调用方（以为是服务端故障），也让 WAF / 监控系统产生大量假告警。
 * QA 越权矩阵已记录该项：预期 404，实际 500。</p>
 *
 * <p>仅 Servlet 应用加载；WebFlux 网关不引入 spring-webmvc，故条件加载跳过。
 * 优先级设为最高，确保先于 {@link GlobalExceptionHandler} 的 Exception 兜底匹配。</p>
 */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
@ConditionalOnClass(name = "org.springframework.web.servlet.resource.NoResourceFoundException")
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class NotFoundExceptionHandler {

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<R<Void>> handleNotFound(NoResourceFoundException e) {
        // 不回显具体路径，避免给探测者反馈站点结构
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(R.fail(ErrorCode.NOT_FOUND.getCode(), ErrorCode.NOT_FOUND.getMessage()));
    }
}
