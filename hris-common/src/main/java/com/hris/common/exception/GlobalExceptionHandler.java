package com.hris.common.exception;

import com.hris.common.api.R;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 全局异常处理器（依赖 spring-web）。将各类异常收敛为统一响应体，系统内部错误不泄露堆栈（traceId 排障）。
 *
 * <p>【仅 Servlet 应用加载】{@link RestControllerAdvice} 位于 spring-web，WebFlux 应用同样有该类，
 * 仅靠 {@code @ConditionalOnClass} 无法区分——hris-gateway 曾因此错误加载本处理器：
 * 它会抢在网关自己的 {@code ErrorWebExceptionHandler} 之前接管异常，把「无路由/无静态资源」
 * 的 404 吞成 500（响应体 code=50001、traceId 为 null），并为每一次 404 打印整段堆栈。
 * 因此必须再叠加 {@code @ConditionalOnWebApplication(SERVLET)}。</p>
 *
 * <p>Jakarta Validation 相关异常（ConstraintViolationException）由 {@link ValidationExceptionHandler} 条件加载。</p>
 */
@RestControllerAdvice
@ConditionalOnClass(RestControllerAdvice.class)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BizException.class)
    public ResponseEntity<R<Void>> handleBiz(BizException e) {
        return ResponseEntity.ok(R.fail(e.getCode(), e.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<R<Void>> handleValid(MethodArgumentNotValidException e) {
        FieldError fe = e.getBindingResult().getFieldError();
        String msg = fe == null ? ErrorCode.PARAM_INVALID.getMessage()
                : fe.getField() + " " + fe.getDefaultMessage();
        return ResponseEntity.badRequest().body(R.fail(ErrorCode.PARAM_INVALID.getCode(), msg));
    }

    @ExceptionHandler(BindException.class)
    public ResponseEntity<R<Void>> handleBind(BindException e) {
        FieldError fe = e.getBindingResult().getFieldError();
        String msg = fe == null ? ErrorCode.PARAM_INVALID.getMessage()
                : fe.getField() + " " + fe.getDefaultMessage();
        return ResponseEntity.badRequest().body(R.fail(ErrorCode.PARAM_INVALID.getCode(), msg));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<R<Void>> handleOther(Exception e) {
        log.error("unhandled exception", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(R.fail(ErrorCode.SYSTEM_ERROR));
    }
}
