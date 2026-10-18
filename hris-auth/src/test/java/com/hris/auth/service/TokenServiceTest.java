package com.hris.auth.service;

import com.hris.auth.config.AuthProperties;
import com.hris.auth.model.LoginUser;
import com.hris.common.exception.BizException;
import com.hris.common.exception.ErrorCode;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * JWT 签发 / 解析 / 撤销单测（docs/16 L1，纯逻辑无 Spring 上下文）。
 *
 * <p>覆盖三类关键守卫：签名/issuer 不可伪造、access 与 refresh 类型不可互用、
 * 登出与轮换后令牌立即失效。</p>
 */
@DisplayName("TokenService：HS256 签发与校验")
class TokenServiceTest {

    /** 与生产同源形态的强密钥（≥32 字节）。 */
    private static final String SECRET = "zhiyun-hris-jwt-test-secret-32bytes-min";
    private static final String ISSUER = "hris-auth";

    private TokenService service;

    @BeforeEach
    void setUp() {
        AuthProperties props = new AuthProperties();
        props.setSecret(SECRET);
        props.setIssuer(ISSUER);
        service = new TokenService(props);
    }

    private LoginUser stubUser() {
        return new LoginUser(100001L, 100001L, 200001L, "admin", 1,
                List.of("ROLE_ADMIN"), List.of("emp:read", "cmp:salary:read"));
    }

    /** 用另一把密钥 + 同一 issuer 自签，模拟攻击者伪造。 */
    private String forgeToken(String secret, String issuer, String type, long ttlSeconds) {
        SecretKey key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        Date now = new Date();
        return Jwts.builder()
                .issuer(issuer)
                .subject("100001")
                .id("forged-jti")
                .claim("typ", type)
                .claim("tenant_id", 999001L)
                .claim("username", "attacker")
                .issuedAt(now)
                .expiration(new Date(now.getTime() + ttlSeconds * 1000))
                .signWith(key, Jwts.SIG.HS256)
                .compact();
    }

    // ==================== 签发 ====================
    @Nested
    @DisplayName("issue 签发")
    class Issue {

        @Test
        @DisplayName("access 令牌携带完整身份声明（租户/员工/权限）")
        void issuesAccessWithFullClaims() {
            String token = service.issueAccess(stubUser());

            Claims c = Jwts.parser().verifyWith(
                            Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)))
                    .build().parseSignedClaims(token).getPayload();

            assertEquals("100001", c.getSubject());
            assertEquals(ISSUER, c.getIssuer());
            assertEquals("access", c.get("typ", String.class));
            assertEquals(100001L, ((Number) c.get("tenant_id")).longValue());
            assertEquals(200001L, ((Number) c.get("emp_id")).longValue());
            assertEquals("admin", c.get("username", String.class));
            assertEquals(List.of("ROLE_ADMIN"), c.get("roles"));
            assertEquals(List.of("emp:read", "cmp:salary:read"), c.get("perms"));
            assertNotNull(c.getId(), "必须带 jti 以支持黑名单");
        }

        @Test
        @DisplayName("算法显式固定 HS256（不随密钥长度漂移到 HS384/512）")
        void pinsHs256Algorithm() {
            String header = new String(Base64.getUrlDecoder().decode(
                    service.issueAccess(stubUser()).split("\\.")[0]), StandardCharsets.UTF_8);
            assertTrue(header.contains("HS256"), "alg 必须是 HS256，实际: " + header);
        }

        @Test
        @DisplayName("access 与 refresh 的 jti 互不相同")
        void issuesDistinctJti() {
            String access = service.issueAccess(stubUser());
            String refresh = service.issueRefresh(100001L, 100001L);

            String accessJti = Jwts.parser().verifyWith(
                            Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)))
                    .build().parseSignedClaims(access).getPayload().getId();
            String refreshJti = Jwts.parser().verifyWith(
                            Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)))
                    .build().parseSignedClaims(refresh).getPayload().getId();

            assertNotEquals(accessJti, refreshJti);
        }

        @Test
        @DisplayName("员工 ID 为 null 时也能签发（系统账号场景）")
        void issuesForAccountWithoutEmployee() {
            LoginUser sys = new LoginUser(100002L, 100001L, null, "system", 1,
                    List.of("ROLE_SYS"), List.of());

            LoginUser parsed = service.parseAccess(service.issueAccess(sys));

            assertEquals("system", parsed.username());
            org.junit.jupiter.api.Assertions.assertNull(parsed.employeeId());
            assertEquals(List.of(), parsed.perms());
        }
    }

    // ==================== 校验与防伪造 ====================
    @Nested
    @DisplayName("parse 校验与防伪造")
    class Parse {

        @Test
        @DisplayName("正确令牌可还原出完整身份")
        void parsesValidToken() {
            LoginUser u = service.parseAccess(service.issueAccess(stubUser()));

            assertEquals(100001L, u.userId());
            assertEquals(100001L, u.tenantId());
            assertEquals(200001L, u.employeeId());
            assertEquals("admin", u.username());
            assertEquals(1, u.status());
            assertEquals(List.of("ROLE_ADMIN"), u.roles());
            assertEquals(List.of("emp:read", "cmp:salary:read"), u.perms());
        }

        @Test
        @DisplayName("换密钥重签的令牌被拒（防伪造身份）")
        void rejectsForeignSignature() {
            String forged = forgeToken("another-secret-that-is-long-enough-32b",
                    ISSUER, "access", 900);

            BizException e = assertThrows(BizException.class,
                    () -> service.parseAccess(forged));
            assertEquals(ErrorCode.UNAUTHORIZED.getCode(), e.getCode());
        }

        @Test
        @DisplayName("issuer 不符被拒（防止其他系统令牌跨域复用）")
        void rejectsWrongIssuer() {
            String forged = forgeToken(SECRET, "someone-else", "access", 900);

            assertThrows(BizException.class, () -> service.parseAccess(forged));
        }

        @Test
        @DisplayName("过期令牌被拒")
        void rejectsExpired() {
            String expired = forgeToken(SECRET, ISSUER, "access", -60);

            BizException e = assertThrows(BizException.class,
                    () -> service.parseAccess(expired));
            assertTrue(e.getMessage().contains("无效或已过期"));
        }

        @Test
        @DisplayName("refresh 令牌不能当 access 用（类型混淆防护）")
        void rejectsRefreshAsAccess() {
            String refresh = service.issueRefresh(100001L, 100001L);

            BizException e = assertThrows(BizException.class,
                    () -> service.parseAccess(refresh));
            assertTrue(e.getMessage().contains("令牌类型错误"));
        }

        @Test
        @DisplayName("access 令牌不能当 refresh 用")
        void rejectsAccessAsRefresh() {
            String access = service.issueAccess(stubUser());

            assertThrows(BizException.class, () -> service.parseRefresh(access));
        }

        @Test
        @DisplayName("畸形/空令牌被拒而非抛底层异常")
        void rejectsMalformed() {
            for (String bad : new String[]{"", "abc", "a.b.c"}) {
                BizException e = assertThrows(BizException.class,
                        () -> service.parseAccess(bad));
                assertEquals(ErrorCode.UNAUTHORIZED.getCode(), e.getCode());
            }
        }
    }

    // ==================== refresh 轮换 ====================
    @Nested
    @DisplayName("refresh 轮换与重用检测")
    class Refresh {

        @Test
        @DisplayName("有效 refresh 可解析出主体")
        void parsesValidRefresh() {
            String token = service.issueRefresh(100001L, 100001L);

            TokenService.RefreshToken rt = service.parseRefresh(token);

            assertEquals(100001L, rt.userId());
            assertEquals(100001L, rt.tenantId());
        }

        @Test
        @DisplayName("轮换后旧 refresh 不可重用（reuse detection）")
        void rejectsReusedRefreshAfterConsume() {
            String old = service.issueRefresh(100001L, 100001L);
            String jti = service.parseRefresh(old).jti();

            service.consumeRefresh(jti);  // 轮换消费

            BizException e = assertThrows(BizException.class,
                    () -> service.parseRefresh(old));
            assertTrue(e.getMessage().contains("已被重用"));
        }

        @Test
        @DisplayName("另一个 refresh 令牌不受影响")
        void keepsOtherRefreshValid() {
            String keep = service.issueRefresh(100001L, 100001L);
            String consumeMe = service.issueRefresh(100001L, 100001L);

            service.consumeRefresh(service.parseRefresh(consumeMe).jti());

            assertNotNull(service.parseRefresh(keep));
        }
    }
}
