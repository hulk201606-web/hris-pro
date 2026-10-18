package com.hris.gateway.jwt;

import com.hris.gateway.common.GatewayConstants;
import com.hris.gateway.config.JwtProperties;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 网关 JWT 验签单测（docs/16 L1）。
 *
 * <p>网关与 hris-auth 是<b>两个独立进程</b>，验签一致性靠「同 secret + 同 issuer +
 * 同 claims 字段名」这一约定维持。本类用 auth 侧真实签发算法构造令牌，
 * 锁定网关的解析契约，防止后续单侧改动导致 401 潮涌。</p>
 */
@DisplayName("JwtTokenVerifier：网关侧验签")
class JwtTokenVerifierTest {

    private static final String SECRET = "zhiyun-hris-jwt-test-secret-32bytes-min";
    private static final String ISSUER = "hris-auth";

    private JwtTokenVerifier verifier;

    @BeforeEach
    void setUp() {
        JwtProperties props = new JwtProperties();
        props.setSecret(SECRET);
        props.setIssuer(ISSUER);
        verifier = new JwtTokenVerifier(props);
    }

    /** 复刻 hris-auth TokenService.issueAccess 的签发逻辑（同 claims 名与顺序约定）。 */
    private String issueAuthStyleAccess(String secret, String issuer, String type,
                                        Long tenantId, Long empId, String username,
                                        List<String> roles, List<String> perms, long ttlSeconds) {
        SecretKey key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        Date now = new Date();
        var builder = Jwts.builder()
                .issuer(issuer)
                .subject("100001")
                .id("jti-" + tenantId)
                .claim("typ", type)
                .claim("tenant_id", tenantId)
                .claim("username", username)
                .issuedAt(now)
                .expiration(new Date(now.getTime() + ttlSeconds * 1000));
        if (empId != null) {
            builder.claim("emp_id", empId);
        }
        builder.claim("roles", roles).claim("perms", perms);
        return builder.signWith(key, Jwts.SIG.HS256).compact();
    }

    private String validToken() {
        return issueAuthStyleAccess(SECRET, ISSUER, GatewayConstants.TOKEN_TYPE_ACCESS,
                100001L, 200001L, "admin",
                List.of("ROLE_ADMIN"), List.of("emp:read", "cmp:salary:read"), 900);
    }

    @Test
    @DisplayName("auth 风格签发的 access 令牌可被网关验签解析")
    void verifiesAuthIssuedToken() {
        GatewayPrincipal p = verifier.verifyAccess(validToken());

        assertEquals(100001L, p.userId());
        assertEquals(100001L, p.tenantId());
        assertEquals(200001L, p.empId());
        assertEquals(List.of("ROLE_ADMIN"), p.roles());
        assertEquals(List.of("emp:read", "cmp:salary:read"), p.perms());
    }

    @Test
    @DisplayName("跨租户令牌正常解析（租户隔离由后续过滤器负责，非验签职责）")
    void parsesTenantFromToken() {
        String token = issueAuthStyleAccess(SECRET, ISSUER,
                GatewayConstants.TOKEN_TYPE_ACCESS, 200002L, 300002L, "other",
                List.of(), List.of(), 900);

        assertEquals(200002L, verifier.verifyAccess(token).tenantId());
    }

    @Test
    @DisplayName("secret 不一致的令牌被拒（模拟密钥未同步）")
    void rejectsMismatchedSecret() {
        String token = issueAuthStyleAccess("other-secret-long-enough-32-bytes-xx",
                ISSUER, GatewayConstants.TOKEN_TYPE_ACCESS,
                100001L, 200001L, "admin", List.of(), List.of(), 900);

        assertThrows(JwtException.class, () -> verifier.verifyAccess(token));
    }

    @Test
    @DisplayName("issuer 不符被拒")
    void rejectsWrongIssuer() {
        String token = issueAuthStyleAccess(SECRET, "another-issuer",
                GatewayConstants.TOKEN_TYPE_ACCESS,
                100001L, 200001L, "admin", List.of(), List.of(), 900);

        assertThrows(JwtException.class, () -> verifier.verifyAccess(token));
    }

    @Test
    @DisplayName("refresh 令牌被拒（类型混淆防护）")
    void rejectsRefreshToken() {
        // 网关常量只定义 access（其职责即放行 access）；refresh 值为 auth 侧约定
        String token = issueAuthStyleAccess(SECRET, ISSUER, "refresh",
                100001L, 200001L, "admin", List.of(), List.of(), 900);

        JwtException e = assertThrows(JwtException.class,
                () -> verifier.verifyAccess(token));
        assertTrue(e.getMessage().contains("type mismatch"));
    }

    @Test
    @DisplayName("过期令牌被拒")
    void rejectsExpired() {
        String token = issueAuthStyleAccess(SECRET, ISSUER,
                GatewayConstants.TOKEN_TYPE_ACCESS,
                100001L, 200001L, "admin", List.of(), List.of(), -60);

        assertThrows(JwtException.class, () -> verifier.verifyAccess(token));
    }

    @Test
    @DisplayName("无 emp_id 的系统账号令牌可解析（empId 为 null）")
    void toleratesMissingEmpId() {
        String token = issueAuthStyleAccess(SECRET, ISSUER,
                GatewayConstants.TOKEN_TYPE_ACCESS,
                100001L, null, "system", List.of("ROLE_SYS"), List.of(), 900);

        GatewayPrincipal p = verifier.verifyAccess(token);

        assertNull(p.empId());
        assertEquals(100001L, p.userId(), "系统账号无 emp_id 时仍应解析出主体");
    }

    @Test
    @DisplayName("claims 缺失 roles/perms 时降级为空列表（不抛异常）")
    void toleratesMissingRolePerms() {
        SecretKey key = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
        Date now = new Date();
        String token = Jwts.builder()
                .issuer(ISSUER)
                .subject("100001")
                .claim("typ", GatewayConstants.TOKEN_TYPE_ACCESS)
                .claim("tenant_id", 100001L)
                .issuedAt(now)
                .expiration(new Date(now.getTime() + 900_000))
                .signWith(key, Jwts.SIG.HS256)
                .compact();

        GatewayPrincipal p = verifier.verifyAccess(token);

        assertEquals(List.of(), p.roles());
        assertEquals(List.of(), p.perms());
    }

    @Test
    @DisplayName("构造时弱密钥拒绝启动")
    void rejectsWeakSecretAtConstruction() {
        JwtProperties weak = new JwtProperties();
        weak.setSecret("short");

        assertThrows(IllegalStateException.class, () -> new JwtTokenVerifier(weak));
    }
}