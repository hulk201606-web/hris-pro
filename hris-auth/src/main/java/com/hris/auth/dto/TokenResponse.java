package com.hris.auth.dto;

/**
 * 登录 / 刷新令牌响应。
 */
public record TokenResponse(
        String accessToken,
        String refreshToken,
        String tokenType,
        long expiresIn,
        long refreshExpiresIn) {

    public static TokenResponse of(String accessToken, String refreshToken,
                                   long expiresIn, long refreshExpiresIn) {
        return new TokenResponse(accessToken, refreshToken, "Bearer", expiresIn, refreshExpiresIn);
    }
}
