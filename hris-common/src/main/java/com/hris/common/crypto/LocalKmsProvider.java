package com.hris.common.crypto;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Arrays;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 本地 KMS 实现（自托管主密钥，排期批 12 #30）。
 *
 * <p><b>主密钥</b>来自 {@code HRIS_CRYPTO_KEY}（32 字节 Base64，启动期经
 * {@link CryptoKeyGuard#requireStrongKey} 强校验），常驻进程内存、不落任何存储。</p>
 *
 * <p><b>数据密钥</b>为 32 字节，由主密钥确定性派生：
 * {@code DEK = HMAC-SHA256(master, "hris-kms-dek-v1")}。确定性派生是刻意选择——
 * 若 DEK 为随机值且只存内存，进程重启即丢失，库里全部 enc2 密文将不可解；
 * 派生式 DEK 与主密钥同生命周期，重启后按同一 keyId 重新推导。派生结果仍缓存于
 * {@link ConcurrentHashMap}（避免每次解密重复做 HMAC），满足「内存缓存数据密钥」要求。</p>
 *
 * <p><b>密钥版本化</b>：{@code keyId = HMAC-SHA256(master, "hris-kms-kid-v1")} 前 8 字节
 * 的小写十六进制。主密钥轮换 → keyId 变化 → 旧 keyId 密文无法用新主密钥解出，
 * {@link #unwrapKey} 返回 {@code null} 由 {@link CryptoService} 显式报错（不静默）。
 * 生产轮换应在保留旧主密钥的过渡窗口内重加密存量数据（scripts/migrate_encrypt_v2.py）。</p>
 *
 * <p><b>云 KMS 预留实现</b>：替换本类即可，{@link KmsProvider} 契约不变。以阿里云 KMS 为例：
 * {@code GenerateDataKey} 返回明文 DEK + CiphertextBlob（即 wrapKey 结果，落库），
 * {@code Decrypt(CiphertextBlob)} 即 unwrapKey；keyId 取 {@code KeyVersionId}。
 * 该类不做网络 IO 的前提是「全局单 DEK」，云实现同样建议按 keyId 缓存 DEK 并设 TTL。</p>
 */
public final class LocalKmsProvider implements KmsProvider {

    /** keyId 派生标签（改标签等于换一代 KMS 体系，须与迁移脚本保持一致）。 */
    static final String KID_LABEL = "hris-kms-kid-v1";
    /** DEK 派生标签。 */
    static final String DEK_LABEL = "hris-kms-dek-v1";

    static final int KEY_ID_BYTES = 8;
    private static final String HMAC = "HmacSHA256";
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;

    private final SecretKeySpec master;
    private final String keyId;
    private final Map<String, SecretKey> dataKeyCache = new ConcurrentHashMap<>();

    public LocalKmsProvider(byte[] masterKey) {
        if (masterKey == null || masterKey.length != CryptoKeyGuard.KEY_BYTES) {
            throw new IllegalStateException("KMS 主密钥必须为 " + CryptoKeyGuard.KEY_BYTES + " 字节");
        }
        this.master = new SecretKeySpec(masterKey, "AES");
        this.keyId = HexFormat.of().formatHex(Arrays.copyOf(hmac(KID_LABEL), KEY_ID_BYTES));
    }

    @Override
    public String currentKeyId() {
        return keyId;
    }

    /** 当前版本数据密钥（派生 + 内存缓存）。 */
    @Override
    public DataKey generateDataKey() {
        return new DataKey(keyId, dataKeyCache.computeIfAbsent(keyId, k -> deriveDataKey()));
    }

    /**
     * 用主密钥封装 DEK：返回 {@code base64(iv(12B)||ct||tag)}，AAD = keyId。
     * 本地部署 DEK 可确定性派生，故密封串主要用于导出/跨环境传递与云 KMS 契约对齐。
     */
    @Override
    public String wrapKey(SecretKey dataKey) {
        try {
            byte[] iv = new byte[IV_BYTES];
            new java.security.SecureRandom().nextBytes(iv);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, master, new GCMParameterSpec(TAG_BITS, iv));
            cipher.updateAAD(keyId.getBytes(StandardCharsets.UTF_8));
            byte[] sealed = cipher.doFinal(dataKey.getEncoded());
            byte[] out = new byte[iv.length + sealed.length];
            System.arraycopy(iv, 0, out, 0, iv.length);
            System.arraycopy(sealed, 0, out, iv.length, sealed.length);
            return Base64.getEncoder().encodeToString(out);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("数据密钥封装失败", e);
        }
    }

    /**
     * 解封 DEK。keyId 与当前主密钥不匹配返回 {@code null}（主密钥已轮换，旧 DEK 不可推导）；
     * {@code wrappedKey} 为空时按 keyId 从主密钥确定性派生（enc2 密文只携带 keyId）。
     */
    @Override
    public SecretKey unwrapKey(String wrappedKey, String keyId) {
        if (keyId == null || !this.keyId.equals(keyId)) {
            return null;
        }
        if (wrappedKey == null || wrappedKey.isBlank()) {
            return dataKeyCache.computeIfAbsent(keyId, k -> deriveDataKey());
        }
        try {
            byte[] all = Base64.getDecoder().decode(wrappedKey);
            byte[] iv = Arrays.copyOfRange(all, 0, IV_BYTES);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, master, new GCMParameterSpec(TAG_BITS, iv));
            cipher.updateAAD(keyId.getBytes(StandardCharsets.UTF_8));
            byte[] raw = cipher.doFinal(all, IV_BYTES, all.length - IV_BYTES);
            return new SecretKeySpec(raw, "AES");
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            throw new IllegalStateException("数据密钥解封失败（密封串损坏或主密钥不匹配）", e);
        }
    }

    private SecretKey deriveDataKey() {
        return new SecretKeySpec(hmac(DEK_LABEL), "AES");
    }

    /** HMAC-SHA256(master, label) → 32 字节。 */
    private byte[] hmac(String label) {
        try {
            Mac mac = Mac.getInstance(HMAC);
            mac.init(master);
            return mac.doFinal(label.getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HMAC-SHA256 不可用", e);
        }
    }
}
