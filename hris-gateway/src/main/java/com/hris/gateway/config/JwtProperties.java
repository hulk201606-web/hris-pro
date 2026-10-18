package com.hris.gateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 网关 JWT 验签配置。与 hris-auth 的 AuthProperties 对齐：
 * HS256 + 同一 secret（环境变量 JWT_SECRET）+ 同一 issuer。
 */
@Component
@ConfigurationProperties(prefix = "hris.jwt")
public class JwtProperties {

    /** HS256 密钥（至少 32 字节）。生产经环境变量/KMS 注入，默认值须与 auth 服务一致。 */
    private String secret;

    /** 令牌签发方。auth 服务签发时固定为 hris-auth。 */
    private String issuer = "hris-auth";

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
}
