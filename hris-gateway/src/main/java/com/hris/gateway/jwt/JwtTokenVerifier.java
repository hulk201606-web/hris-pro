package com.hris.gateway.jwt;

import com.hris.common.security.JwtSecretGuard;
import com.hris.gateway.common.GatewayConstants;
import com.hris.gateway.config.JwtProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;

/**
 * 网关 JWT 验签器（HS256）。与 hris-auth TokenService 的解析逻辑对齐：
 * 同一 secret、同一 issuer、同一 claims 字段名、access 类型校验。
 *
 * <p>MVP 说明：网关仅做签名/过期/issuer/类型校验；登出/禁用黑名单在 auth 进程内（内存），
 * 网关不感知，access 令牌在 TTL 内仍可通行（需 Redis 共享黑名单方可即时失效，见 docs/08 §1）。</p>
 */
@Component
public class JwtTokenVerifier {

    private final SecretKey key;
    private final String issuer;

    public JwtTokenVerifier(JwtProperties props) {
        this.key = Keys.hmacShaKeyFor(
                JwtSecretGuard.requireStrongSecret(props.getSecret()).getBytes(StandardCharsets.UTF_8));
        this.issuer = props.getIssuer();
    }

    /**
     * 校验 access 令牌并解析主体。校验失败抛 {@link JwtException} 或 {@link IllegalArgumentException}。
     */
    public GatewayPrincipal verifyAccess(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(key)
                .requireIssuer(issuer)
                .build()
                .parseSignedClaims(token)
                .getPayload();

        String type = claims.get(GatewayConstants.CLAIM_TYPE, String.class);
        if (!GatewayConstants.TOKEN_TYPE_ACCESS.equals(type)) {
            throw new JwtException("token type mismatch, expected access");
        }

        Long userId = Long.parseLong(claims.getSubject());
        Long tenantId = ((Number) claims.get(GatewayConstants.CLAIM_TENANT_ID)).longValue();
        Long empId = claims.get(GatewayConstants.CLAIM_EMP_ID) == null
                ? null
                : ((Number) claims.get(GatewayConstants.CLAIM_EMP_ID)).longValue();
        return new GatewayPrincipal(userId, tenantId, empId, claims.getId());
    }
}
