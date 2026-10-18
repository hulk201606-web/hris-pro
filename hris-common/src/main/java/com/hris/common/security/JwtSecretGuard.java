package com.hris.common.security;

import java.nio.charset.StandardCharsets;

/**
 * JWT HS256 密钥守卫。启动期强校验：拒绝空值、弱密钥与代码库内已知默认值，
 * 防止攻击者用公开默认值自签令牌伪造身份（越权零容忍）。
 */
public final class JwtSecretGuard {

    /** 代码库历史默认值，一经检出即拒绝启动。 */
    public static final String KNOWN_DEFAULT = "hris-auth-hs256-secret-change-me-0123456789";

    private static final int MIN_BYTES = 32;

    private JwtSecretGuard() {
    }

    /**
     * 校验并返回密钥。非法场景抛 {@link IllegalStateException}，使服务拒绝启动。
     */
    public static String requireStrongSecret(String secret) {
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException("生产环境必须通过 JWT_SECRET 注入强随机密钥");
        }
        if (secret.getBytes(StandardCharsets.UTF_8).length < MIN_BYTES) {
            throw new IllegalStateException("JWT_SECRET 长度不足 32 字节，必须注入强随机密钥");
        }
        if (KNOWN_DEFAULT.equals(secret)) {
            throw new IllegalStateException("禁止使用默认 JWT_SECRET，生产环境必须通过 JWT_SECRET 注入强随机密钥");
        }
        return secret;
    }
}
