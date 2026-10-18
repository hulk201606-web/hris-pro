package com.hris.auth.service;

import com.hris.auth.config.AuthProperties;
import com.hris.auth.model.LoginUser;
import com.hris.common.exception.BizException;
import com.hris.common.exception.ErrorCode;
import com.hris.common.security.JwtSecretGuard;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 令牌撤销与启动期守卫单测（docs/16 L1）。
 *
 * <p>登出必须做到「即时失效 + 幂等 + 不误伤他人令牌」；服务在密钥不合规时必须
 * 拒绝构造（而非降级运行），这两条都是安全基线。</p>
 */
@DisplayName("TokenService：撤销与启动守卫")
class TokenServiceRevokeTest {

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

    @Nested
    @DisplayName("revoke 登出撤销")
    class Revoke {

        @Test
        @DisplayName("登出后 access 令牌立即失效")
        void revokesAccess() {
            String access = service.issueAccess(stubUser());

            service.revoke(access, null);

            BizException e = assertThrows(BizException.class,
                    () -> service.parseAccess(access));
            assertTrue(e.getMessage().contains("令牌已失效"));
        }

        @Test
        @DisplayName("登出同时使 refresh 失效")
        void revokesBoth() {
            String access = service.issueAccess(stubUser());
            String refresh = service.issueRefresh(100001L, 100001L);

            service.revoke(access, refresh);

            assertThrows(BizException.class, () -> service.parseAccess(access));
            assertThrows(BizException.class, () -> service.parseRefresh(refresh));
        }

        @Test
        @DisplayName("撤销无效/过期/空令牌不抛异常（登出接口须幂等）")
        void revokeIsIdempotent() {
            service.revoke("garbage", null);
            service.revoke(null, null);
            service.revoke("", "  ");
        }

        @Test
        @DisplayName("撤销只影响目标令牌，不误伤他人会话")
        void revokeOnlyTargetsGivenToken() {
            String mine = service.issueAccess(stubUser());
            String others = service.issueAccess(new LoginUser(
                    100009L, 100001L, 200009L, "other", 1, List.of(), List.of()));

            service.revoke(mine, null);

            assertThrows(BizException.class, () -> service.parseAccess(mine));
            assertNotNull(service.parseAccess(others));
        }
    }

    @Nested
    @DisplayName("跨服务同源契约")
    class CrossService {

        @Test
        @DisplayName("claims 名称与权限透传约定被锁定（网关据此鉴权与隔离）")
        void locksClaimsContract() {
            LoginUser u = service.parseAccess(service.issueAccess(stubUser()));

            assertEquals(100001L, u.tenantId(), "租户声明须可还原");
            assertEquals(List.of("emp:read", "cmp:salary:read"), u.perms(),
                    "权限声明须以字符串数组原样透传");
        }
    }

    @Nested
    @DisplayName("启动期守卫")
    class StartupGuard {

        @Test
        @DisplayName("弱密钥直接拒绝构造（服务不降级启动）")
        void rejectsWeakSecret() {
            AuthProperties weak = new AuthProperties();
            weak.setSecret("too-short");

            assertThrows(IllegalStateException.class, () -> new TokenService(weak));
        }

        @Test
        @DisplayName("代码库默认密钥直接拒绝构造")
        void rejectsKnownDefaultSecret() {
            AuthProperties weak = new AuthProperties();
            weak.setSecret(JwtSecretGuard.KNOWN_DEFAULT);

            assertThrows(IllegalStateException.class, () -> new TokenService(weak));
        }

        @Test
        @DisplayName("未注入密钥（null）拒绝构造")
        void rejectsMissingSecret() {
            AuthProperties none = new AuthProperties();

            assertThrows(IllegalStateException.class, () -> new TokenService(none));
        }

        @Test
        @DisplayName("TTL 配置对外暴露且 refresh 长于 access")
        void exposesTtl() {
            assertTrue(service.accessTtlSeconds() > 0);
            assertTrue(service.refreshTtlSeconds() > service.accessTtlSeconds(),
                    "refresh 有效期必须长于 access");
        }
    }

    @Test
    @DisplayName("错误码契约：解析失败一律 UNAUTHORIZED")
    void usesUnauthorizedCode() {
        BizException e = assertThrows(BizException.class,
                () -> service.parseAccess("not-a-token"));
        assertEquals(ErrorCode.UNAUTHORIZED.getCode(), e.getCode());
    }
}