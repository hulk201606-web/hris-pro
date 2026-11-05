package com.hris.common.crypto;

import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 密钥守卫单测：空值 / 非法 Base64 / 长度不合规 / 全零弱密钥 / 已知默认值均拒绝；合法值放行。
 */
class CryptoKeyGuardTest {

    @Test
    void rejectsNullAndBlank() {
        assertThrows(IllegalStateException.class, () -> CryptoKeyGuard.requireStrongKey(null));
        assertThrows(IllegalStateException.class, () -> CryptoKeyGuard.requireStrongKey(""));
        assertThrows(IllegalStateException.class, () -> CryptoKeyGuard.requireStrongKey("   "));
    }

    @Test
    void rejectsInvalidBase64() {
        assertThrows(IllegalStateException.class,
                () -> CryptoKeyGuard.requireStrongKey("这不是base64!!!"));
    }

    @Test
    void rejectsWrongLength() {
        // 16 字节 -> AES-128，不符合 AES-256 要求
        String shortKey = Base64.getEncoder().encodeToString(new byte[16]);
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> CryptoKeyGuard.requireStrongKey(shortKey));
        assertEquals(true, ex.getMessage().contains("32"));
        // 64 字节超长同样拒绝
        String longKey = Base64.getEncoder().encodeToString(new byte[64]);
        assertThrows(IllegalStateException.class, () -> CryptoKeyGuard.requireStrongKey(longKey));
    }

    @Test
    void rejectsAllZeroAndRepeatedByteKeys() {
        assertThrows(IllegalStateException.class, () -> CryptoKeyGuard.requireStrongKey(
                Base64.getEncoder().encodeToString(new byte[32])));
        assertThrows(IllegalStateException.class, () -> CryptoKeyGuard.requireStrongKey(
                Base64.getEncoder().encodeToString(new byte[32])));
    }

    @Test
    void rejectsKnownWeakKeys() {
        for (String weak : CryptoKeyGuard.KNOWN_WEAK_KEYS) {
            assertThrows(IllegalStateException.class, () -> CryptoKeyGuard.requireStrongKey(weak));
        }
    }

    @Test
    void acceptsStrongKeyAndRoundTrips() {
        byte[] key = new byte[32];
        new java.security.SecureRandom().nextBytes(key);
        String base64 = Base64.getEncoder().encodeToString(key);
        assertArrayEquals(key, assertDoesNotThrow(() -> CryptoKeyGuard.requireStrongKey(base64)));
    }

    @Test
    void generatedKeyPassesGuard() {
        String generated = CryptoKeyGuard.generateKey();
        assertDoesNotThrow(() -> CryptoKeyGuard.requireStrongKey(generated));
        assertEquals(32, CryptoKeyGuard.requireStrongKey(generated).length);
    }
}
