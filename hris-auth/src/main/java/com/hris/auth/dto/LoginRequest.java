package com.hris.auth.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 账号密码登录请求（MVP 简化版）。验证码/MFA 逐步认证在后续增量接入。
 */
public record LoginRequest(
        @NotBlank(message = "租户编码不能为空") String tenantCode,
        @NotBlank(message = "用户名不能为空") String username,
        @NotBlank(message = "密码不能为空") String password) {
}
