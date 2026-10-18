package com.hris.common.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * JWT 密钥守卫测试（docs/16 L1）。
 *
 * <p>这是全系统的越权零容忍门禁：密钥不合规必须让服务<b>拒绝启动</b>，
 * 而不是降级告警——否则攻击者可用公开默认值自签令牌伪造任意租户身份。</p>
 */
@DisplayName("JwtSecretGuard：启动期密钥强校验")
class JwtSecretGuardTest {

    /** 合规密钥（32 字节以上）。 */
    private static final String STRONG = "zhiyun-hris-prod-jwt-secret-2026-rotate-me";

    @Test
    @DisplayName("合规密钥原样返回")
    void acceptsStrongSecret() {
        assertEquals(STRONG, JwtSecretGuard.requireStrongSecret(STRONG));
    }

    @ParameterizedTest(name = "非法密钥 [{index}] {0} → 拒绝启动")
    @ValueSource(strings = {"short", "1234567890123456789012345678901"})
    @DisplayName("长度不足 32 字节 → IllegalStateException")
    void rejectsTooShort(String secret) {
        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> JwtSecretGuard.requireStrongSecret(secret));
        assertTrue(e.getMessage().contains("32 字节"));
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "   "})
    @DisplayName("空值/空白 → IllegalStateException")
    void rejectsBlank(String secret) {
        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> JwtSecretGuard.requireStrongSecret(secret));
        assertTrue(e.getMessage().contains("强随机密钥"));
    }

    @Test
    @DisplayName("代码库已知默认值 → 拒绝启动（防默认密钥攻击）")
    void rejectsKnownDefault() {
        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> JwtSecretGuard.requireStrongSecret(JwtSecretGuard.KNOWN_DEFAULT));
        assertTrue(e.getMessage().contains("禁止使用默认"));
    }

    @Test
    @DisplayName("恰好 32 字节的边界值应通过（不误杀合法配置）")
    void acceptsExactThirtyTwoBytes() {
        String exact = "a".repeat(32);
        assertEquals(32, exact.getBytes(StandardCharsets.UTF_8).length);
        assertEquals(exact, JwtSecretGuard.requireStrongSecret(exact));
    }

    @Test
    @DisplayName("31 字节的边界值应拒绝")
    void rejectsThirtyOneBytes() {
        assertThrows(IllegalStateException.class,
                () -> JwtSecretGuard.requireStrongSecret("a".repeat(31)));
    }

    @Test
    @DisplayName("多字节字符按 UTF-8 字节数计（中文密钥 11 字 = 33 字节应通过）")
    void countsUtf8BytesNotChars() {
        String chinese = "智云人力资源系统密钥测试专用值";  // 15 字 × 3 字节 = 45 字节
        assertTrue(chinese.length() < 32, "字符数不足 32，但字节数足够");
        assertEquals(chinese, JwtSecretGuard.requireStrongSecret(chinese));
    }
}