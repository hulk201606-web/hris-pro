package com.hris.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * auth 服务配置（JWT 密钥与令牌有效期，docs/08 §1：access 15min / refresh 7d）。
 */
@ConfigurationProperties(prefix = "auth.jwt")
public class AuthProperties {

    /** HS256 密钥（至少 32 字节） */
    private String secret;

    /** 令牌签发方 */
    private String issuer = "hris-auth";

    /** access 令牌有效期（秒） */
    private long accessTtlSeconds = 900;

    /** refresh 令牌有效期（秒） */
    private long refreshTtlSeconds = 604800;

    public String getSecret() {
        return secret;
    }

    public void setSecret(String secret) {
        this.secret = secret;
    }

    public String getIssuer() {
        return issuer;
    }

    public void setIssuer(String issuer) {
        this.issuer = issuer;
    }

    public long getAccessTtlSeconds() {
        return accessTtlSeconds;
    }

    public void setAccessTtlSeconds(long accessTtlSeconds) {
        this.accessTtlSeconds = accessTtlSeconds;
    }

    public long getRefreshTtlSeconds() {
        return refreshTtlSeconds;
    }

    public void setRefreshTtlSeconds(long refreshTtlSeconds) {
        this.refreshTtlSeconds = refreshTtlSeconds;
    }
}
