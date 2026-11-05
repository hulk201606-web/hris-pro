package com.hris.common.crypto;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.security.GeneralSecurityException;
import java.util.Base64;
import java.security.MessageDigest;
import java.util.HexFormat;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * 敏感列应用层加密服务（ADR-007：AES-256-GCM）。
 *
 * <p>密文格式：{@code enc1:base64(iv(12B) || ciphertext || tag(16B))}，{@code enc1:} 前缀
 * 为版本标记，用于双读兼容（无前缀 = 存量明文，读侧原样返回）。AAD = {@code 表名:逻辑列名}
 * （见 {@link CipherTypeHandler}），绑定密文归属列，防密文挪列重放。</p>
 *
 * <p>实例由 {@link CryptoAutoConfig} 创建：HRIS_CRYPTO_KEY 存在时启用加密（弱密钥经
 * {@link CryptoKeyGuard} 拒绝启动）；未配置时进入「迁移期明文兼容模式」——读写均原样透传，
 * 行为与加密上线前一致（单测与本地开发无需密钥）。生产可设 HRIS_CRYPTO_REQUIRED=true
 * 强制要求密钥。</p>
 *
 * <p>TypeHandler 由 MyBatis 反射实例化（无 Spring 上下文），故用静态单例桥接：
 * {@link #get()} 返回 {@link CryptoAutoConfig} 装配的实例。</p>
 */
public final class CryptoService {

    /** 密文版本前缀（双读兼容的判定标记）。 */
    public static final String PREFIX = "enc1:";

    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;

    private static volatile CryptoService instance;

    private final SecretKeySpec key;
    private final boolean enabled;
    private final SecureRandom random = new SecureRandom();

    public CryptoService(byte[] keyBytes) {
        this.key = new SecretKeySpec(keyBytes, "AES");
        this.enabled = true;
    }

    private CryptoService() {
        this.key = null;
        this.enabled = false;
    }

    /** 迁移期明文兼容模式实例（未配置 HRIS_CRYPTO_KEY 时）。 */
    public static CryptoService disabled() {
        return new CryptoService();
    }

    public boolean isEnabled() {
        return enabled;
    }

    /** 是否为密文格式（带 enc1: 前缀）。 */
    public static boolean isCiphertext(String value) {
        return value != null && value.startsWith(PREFIX);
    }

    /**
     * 加密。null/空串原样返回；已是密文格式幂等返回（防迁移期混写双加密）；
     * 兼容模式下透传明文。返回 {@code enc1:base64(iv||ct||tag)}。
     */
    public String encrypt(String plain, String aad) {
        if (plain == null || plain.isEmpty()) {
            return plain;
        }
        if (!enabled || isCiphertext(plain)) {
            return plain;
        }
        try {
            byte[] iv = new byte[IV_BYTES];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            cipher.updateAAD(aad.getBytes(StandardCharsets.UTF_8));
            byte[] ct = cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8));
            byte[] out = new byte[iv.length + ct.length];
            System.arraycopy(iv, 0, out, 0, iv.length);
            System.arraycopy(ct, 0, out, iv.length, ct.length);
            return PREFIX + Base64.getEncoder().encodeToString(out);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("敏感列加密失败", e);
        }
    }

    /**
     * 解密读侧值（双读兼容）：无前缀 = 存量明文，原样返回；有前缀 = 密文，GCM 验签解密。
     * 兼容模式下明文透传；若库里已存在密文而服务未配置密钥，抛异常（fail loud，静默返回
     * 密文会导致脱敏/比对逻辑在错误数据上继续运行）。
     *
     * <p>与 ADR「try decrypt 失败按明文返回」的差异：存量行由无前缀标记先行分流，带前缀但
     * 验签失败说明密钥错误或数据被篡改，必须显式失败而非把密文当明文下发。</p>
     */
    public String decryptToPlain(String stored, String aad) {
        if (stored == null || stored.isEmpty()) {
            return stored;
        }
        if (!isCiphertext(stored)) {
            return stored;
        }
        if (!enabled) {
            throw new IllegalStateException("数据库存在密文但 HRIS_CRYPTO_KEY 未配置（解密需要主密钥）");
        }
        try {
            byte[] all = Base64.getDecoder().decode(stored.substring(PREFIX.length()));
            byte[] iv = new byte[IV_BYTES];
            System.arraycopy(all, 0, iv, 0, IV_BYTES);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            cipher.updateAAD(aad.getBytes(StandardCharsets.UTF_8));
            byte[] plain = cipher.doFinal(all, IV_BYTES, all.length - IV_BYTES);
            return new String(plain, StandardCharsets.UTF_8);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("敏感列解密失败（密钥不匹配或密文被篡改，AAD=" + aad + "）", e);
        }
    }

    /** SHA-256 小写十六进制（等值查询 hash 列口径，与 cert_no_hash 一致）。 */
    public static String sha256Hex(String plain) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(plain.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("SHA-256 不可用", e);
        }
    }

    /** TypeHandler 桥接入口：返回 CryptoAutoConfig 装配的实例。 */
    public static CryptoService get() {
        CryptoService svc = instance;
        if (svc == null) {
            throw new IllegalStateException("CryptoService 尚未初始化（须由 CryptoAutoConfig 装配）");
        }
        return svc;
    }

    /** 仅 CryptoAutoConfig 调用。 */
    static void setInstance(CryptoService svc) {
        instance = svc;
    }
}
