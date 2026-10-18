package com.hris.auth.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * MFA（TOTP）相关 DTO。
 */
public final class MfaDtos {

    private MfaDtos() {
    }

    /** 绑定响应：返回 TOTP 密钥与 otpauth URI 供扫码。 */
    public record MfaBindResponse(String secret, String otpauthUrl) {
    }

    /** 校验请求：对已绑定密钥校验 6 位 TOTP 动态码。 */
    public record MfaVerifyRequest(@NotBlank(message = "验证码不能为空") String code) {
    }
}
