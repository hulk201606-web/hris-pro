package com.hris.common.crypto;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Set;

/**
 * 加密主密钥守卫（ADR-007 §2，照 {@link com.hris.common.security.JwtSecretGuard} 范式）。
 *
 * <p>主密钥来自环境变量 {@code HRIS_CRYPTO_KEY}：32 字节随机值的 Base64 编码（43~44 字符）。
 * 启动期强校验：拒绝空值、非法 Base64、长度不合规、全零/单字节重复等弱密钥与已知默认值，
 * 非法场景抛 {@link IllegalStateException} 使服务拒绝启动（fail-fast，弱密钥与无密钥同样危险）。</p>
 */
public final class CryptoKeyGuard {

    /** AES-256 要求密钥恰为 32 字节。 */
    public static final int KEY_BYTES = 32;

    /** 已知弱值黑名单：代码示例/文档占位值，一经检出即拒绝启动。 */
    public static final Set<String> KNOWN_WEAK_KEYS = Set.of(
            "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
            "MTIzNDU2Nzg5MDEyMzQ1Njc4OTAxMjM0NTY3ODkwMTI=");

    private CryptoKeyGuard() {
    }

    /**
     * 校验并解码 Base64 主密钥。返回 32 字节密钥；非法场景抛 {@link IllegalStateException}。
     */
    public static byte[] requireStrongKey(String base64Key) {
        if (base64Key == null || base64Key.isBlank()) {
            throw new IllegalStateException("生产环境必须通过 HRIS_CRYPTO_KEY 注入 32 字节随机密钥的 Base64 编码");
        }
        String trimmed = base64Key.trim();
        if (KNOWN_WEAK_KEYS.contains(trimmed)) {
            throw new IllegalStateException("禁止使用已知弱 HRIS_CRYPTO_KEY，必须注入强随机密钥");
        }
        byte[] key;
        try {
            key = Base64.getDecoder().decode(trimmed);
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("HRIS_CRYPTO_KEY 不是合法 Base64 编码", e);
        }
        if (key.length != KEY_BYTES) {
            throw new IllegalStateException("HRIS_CRYPTO_KEY 解码后必须为 " + KEY_BYTES
                    + " 字节（AES-256），实际 " + key.length + " 字节");
        }
        if (isDegenerate(key)) {
            throw new IllegalStateException("HRIS_CRYPTO_KEY 为全零/单字节重复等弱密钥，必须注入强随机密钥");
        }
        return key;
    }

    /** 弱密钥判定：全字节相同（含全零）即视为未注入真实随机性。 */
    private static boolean isDegenerate(byte[] key) {
        byte first = key[0];
        for (byte b : key) {
            if (b != first) {
                return false;
            }
        }
        return true;
    }

    /** 生成一把新的强随机主密钥（Base64），供运维生成 HRIS_CRYPTO_KEY 使用。 */
    public static String generateKey() {
        byte[] key = new byte[KEY_BYTES];
        new java.security.SecureRandom().nextBytes(key);
        return Base64.getEncoder().encodeToString(key);
    }

    /** 十六进制小写（迁移脚本与单测比对用）。 */
    public static String toHex(byte[] bytes) {
        return HexFormat.of().formatHex(bytes);
    }

    /** UTF-8 字节。 */
    public static byte[] utf8(String s) {
        return s.getBytes(StandardCharsets.UTF_8);
    }
}
