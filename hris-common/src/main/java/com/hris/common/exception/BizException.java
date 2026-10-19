package com.hris.common.exception;

/**
 * 业务异常（common 公共库 · 异常域）。
 *
 * <p>所有业务模块在业务校验失败时抛出此异常（如租户失效、账号禁用、密码错误、
 * 权限不足、薪酬密域越权等），由 {@link GlobalExceptionHandler} 统一捕获并转换为
 * {@code R.fail(code, message)} 响应体，HTTP 状态码恒为 200（错误语义在 body.code 中），
 * 与网关/WebFlux 侧的 R 响应体保持一致。</p>
 *
 * <p>多租户场景：异常本身不携带租户上下文，租户信息由 TraceContext 在日志中打印。</p>
 */
public class BizException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /** 业务错误码（对应 R.code），分段见 ErrorCode 枚举。 */
    private final int code;

    /**
     * 以标准 ErrorCode 抛出业务异常（使用枚举自带文案）。
     *
     * @param errorCode 业务错误码枚举，如 UNAUTHORIZED / FORBIDDEN / TENANT_INVALID
     */
    public BizException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.code = errorCode.getCode();
    }

    /**
     * 以 ErrorCode + 自定义详情抛出业务异常（在标准错误码下补充具体业务描述）。
     *
     * @param errorCode 业务错误码枚举
     * @param detail    面向用户的可读详情，如「租户已停用」「用户名已存在」
     */
    public BizException(ErrorCode errorCode, String detail) {
        super(detail);
        this.code = errorCode.getCode();
    }

    /**
     * 直接以错误码 + 文案抛出（不在 ErrorCode 枚举内的动态错误码场景）。
     *
     * @param code    业务错误码
     * @param message 面向用户的可读消息
     */
    public BizException(int code, String message) {
        super(message);
        this.code = code;
    }

    /**
     * @return 业务错误码，GlobalExceptionHandler 用其组装 R.code
     */
    public int getCode() {
        return code;
    }
}
