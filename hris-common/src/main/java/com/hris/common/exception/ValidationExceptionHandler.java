package com.hris.common.exception;

import com.hris.common.api.R;
import jakarta.validation.ConstraintViolationException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Jakarta Validation 异常处理器（依赖 spring-boot-starter-validation）。
 * 仅当存在 jakarta.validation（ConstraintViolationException）时加载；
 * 服务未引入 validation 时（如 hris-flow），本类自动跳过，避免 NoClassDefFoundError。
 *
 * <p>与 {@link GlobalExceptionHandler} 同理，叠加 {@code SERVLET} 条件：
 * WebFlux 网关不应加载 Servlet 侧的 ControllerAdvice。</p>
 */
@RestControllerAdvice
@ConditionalOnClass(ConstraintViolationException.class)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class ValidationExceptionHandler {

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<R<Void>> handleConstraint(ConstraintViolationException e) {
        return ResponseEntity.badRequest()
                .body(R.fail(ErrorCode.PARAM_INVALID.getCode(), e.getMessage()));
    }
}
