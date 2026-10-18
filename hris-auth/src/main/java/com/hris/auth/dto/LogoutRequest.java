package com.hris.auth.dto;

/**
 * 登出请求（access 令牌从 Authorization 头取，refresh 令牌用于撤销族）。
 */
public record LogoutRequest(String refreshToken) {
}
