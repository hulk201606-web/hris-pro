package com.hris.common.crypto;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.HexFormat;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * 敏感列应用层加密服务（ADR-007：AES-256-GCM；排期批 12 #30：升级为 KMS 信封加密）。
 *
 * <p><b>密文格式</b></p>
 * <ul>
 *   <li>v2（新写一律此格式）：{@code enc2:base64(keyId(8B) || iv(12B) || ct || tag(16B))}，
 *       数据密钥来自 {@link KmsProvider}（当前 {@link LocalKmsProvider}）；</li>
 *   <li>v1（存量只读）：{@code enc1:base64(iv(12B) || ct || tag)}，用主密钥直接加解密，
 *       逻辑原样保留，仅用于双读兼容，不再产生新 v1 密文；</li>
 *   <li>无前缀：迁移期存量明文，读侧原样返回。</li>
 * </ul>
 *
 * <p><b>全局单数据密钥</b>：本版本所有租户共用一个 DEK（按 keyId 缓存），换取实现简单与
 * 迁移成本最低；租户级 DEK 需在密文里同时携带租户标识、并按租户管理密钥生命周期，
 * 留待云 KMS 接入时一并演进（已登记 OPEN-DECISIONS）。</p>
 *
 * <p><b>密钥版本化</b>：keyId 随主密钥变化。读 enc2 时先比对 keyId，不匹配（主密钥已轮换
 * 且旧密钥未保留）→ {@link KmsProvider#unwrapKey} 返回 null → 抛 {@link IllegalStateException}；
 * 绝不静默按错误密钥继续。</p>
 *
 * <p>AAD 仍为 {@code 表名:逻辑列名}（见 {@link CipherTypeHandler}）；行级绑定（含主键）
 * 在 TypeHandler 层拿不到主键，继续登记 OPEN-DECISIONS，未在本批改动。</p>
 *
 * <p>实例由 {@link CryptoAutoConfig} 创建：HRIS_CRYPTO_KEY 存在时启用加密（弱密钥经
 * {@link CryptoKeyGuard} 拒绝启动）；未配置时进入「迁移期明文兼容模式」——读写均原样透传。
 * 生产可设 HRIS_CRYPTO_REQUIRED=true 强制要求密钥。</p>
 *
 * <p>TypeHandler 由 MyBatis 反射实例化（无 Spring 上下文），故用静态单例桥接：
 * {@link #get()} 返回 {@link CryptoAutoConfig} 装配的实例。</p>
 */
public final class CryptoService {

    /** v1 密文前缀（存量双读兼容标记，不再产生新数据）。 */
    public static final String PREFIX = "enc1:";
    /** v2 密文前缀（信封加密，新写一律此格式）。 */
    public static final String PREFIX_V2 = "enc2:";

    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;
    /** enc2 头部 keyId 长度（8 字节）。 */
    static final int KEY_ID_BYTES = LocalKmsProvider.KEY_ID_BYTES;

    private static volatile CryptoService instance;

    private final KmsProvider kms;
    /** 仅用于 enc1 存量密文解密；由 KmsProvider 构造时为空（enc1 将 fail loud）。 */
    private final SecretKeySpec legacyKey;
    private final boolean enabled;
    private final SecureRandom random = new SecureRandom();

    /** 主密钥字节入口（兼容旧装配与单测）：内部构造本地 KMS，并保留 enc1 直解能力。 */
    public CryptoService(byte[] keyBytes) {
        this(new LocalKmsProvider(keyBytes), new SecretKeySpec(keyBytes, "AES"));
    }

    /** KMS 入口：enc1 存量密文无主密钥可用（应先行跑 migrate_encrypt_v2.py）。 */
    public CryptoService(KmsProvider kms) {
        this(kms, null);
    }

    private CryptoService(KmsProvider kms, SecretKeySpec legacyKey) {
        this.kms = kms;
        this.legacyKey = legacyKey;
        this.enabled = true;
    }

    private CryptoService() {
        this.kms = null;
        this.legacyKey = null;
        this.enabled = false;
    }

    /** 迁移期明文兼容模式实例（未配置 HRIS_CRYPTO_KEY 时）。 */
    public static CryptoService disabled() {
        return new CryptoService();
    }

    public boolean isEnabled() {
        return enabled;
    }

    /** 是否为密文格式（enc1 / enc2 前缀任一）。 */
    public static boolean isCiphertext(String value) {
        return value != null && (value.startsWith(PREFIX_V2) || value.startsWith(PREFIX));
    }

    /** 当前主密钥版本标识（观测/迁移脚本核对用）。 */
    public String currentKeyId() {
        return enabled ? kms.currentKeyId() : null;
    }

    /**
     * 加密（信封）：null/空串原样返回；已是密文幂等返回（防迁移期混写双加密）；
     * 兼容模式下透传明文。返回 {@code enc2:base64(keyId(8)||iv(12)||ct||tag)}。
     */
    public String encrypt(String plain, String aad) {
        if (plain == null || plain.isEmpty()) {
            return plain;
        }
        if (!enabled || isCiphertext(plain)) {
            return plain;
        }
        KmsProvider.DataKey dataKey = kms.generateDataKey();
        byte[] kid = HexFormat.of().parseHex(dataKey.keyId());
        if (kid.length != KEY_ID_BYTES) {
            throw new IllegalStateException("KMS keyId 长度异常：" + dataKey.keyId());
        }
        try {
            byte[] iv = new byte[IV_BYTES];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, dataKey.key(), new GCMParameterSpec(TAG_BITS, iv));
            cipher.updateAAD(aad.getBytes(StandardCharsets.UTF_8));
            byte[] ct = cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8));
            byte[] out = new byte[kid.length + iv.length + ct.length];
            System.arraycopy(kid, 0, out, 0, kid.length);
            System.arraycopy(iv, 0, out, kid.length, iv.length);
            System.arraycopy(ct, 0, out, kid.length + iv.length, ct.length);
            return PREFIX_V2 + Base64.getEncoder().encodeToString(out);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("敏感列加密失败", e);
        }
    }

    /**
     * 解密读侧值（双读兼容）：无前缀 = 存量明文原样返回；enc2 = 信封解密；
     * enc1 = 旧主密钥直解（存量兼容）。兼容模式下明文透传；库里已存在密文而服务
     * 未配置密钥时抛异常（fail loud）。
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
        return stored.startsWith(PREFIX_V2)
                ? decryptV2(stored.substring(PREFIX_V2.length()), aad)
                : decryptV1(stored.substring(PREFIX.length()), aad);
    }

    /** enc2：keyId(8) || iv(12) || ct || tag，AAD 与 v1 一致。 */
    private String decryptV2(String b64, String aad) {
        try {
            byte[] all = Base64.getDecoder().decode(b64);
            int min = KEY_ID_BYTES + IV_BYTES + TAG_BITS / 8;
            if (all.length < min) {
                throw new IllegalStateException("enc2 密文长度不足（" + all.length + " < " + min + "）");
            }
            String keyId = HexFormat.of().formatHex(Arrays.copyOfRange(all, 0, KEY_ID_BYTES));
            SecretKey dek = kms.unwrapKey(null, keyId);
            if (dek == null) {
                throw new IllegalStateException("密文 keyId=" + keyId + " 与当前主密钥 keyId="
                        + kms.currentKeyId() + " 不匹配（主密钥已轮换且旧密钥未保留），拒绝解密");
            }
            byte[] iv = Arrays.copyOfRange(all, KEY_ID_BYTES, KEY_ID_BYTES + IV_BYTES);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, dek, new GCMParameterSpec(TAG_BITS, iv));
            cipher.updateAAD(aad.getBytes(StandardCharsets.UTF_8));
            byte[] plain = cipher.doFinal(all, KEY_ID_BYTES + IV_BYTES,
                    all.length - KEY_ID_BYTES - IV_BYTES);
            return new String(plain, StandardCharsets.UTF_8);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("敏感列解密失败（密钥不匹配或密文被篡改，AAD=" + aad + "）", e);
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("enc2 密文不是合法 Base64（AAD=" + aad + "）", e);
        }
    }

    /** enc1：旧主密钥直解（存量兼容，密文被挪列/篡改同样验签失败）。 */
    private String decryptV1(String b64, String aad) {
        if (legacyKey == null) {
            throw new IllegalStateException("存在 enc1 存量密文但当前装配无主密钥直解能力，"
                    + "请先执行 scripts/migrate_encrypt_v2.py 迁移（AAD=" + aad + "）");
        }
        try {
            byte[] all = Base64.getDecoder().decode(b64);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, legacyKey, new GCMParameterSpec(TAG_BITS,
                    Arrays.copyOfRange(all, 0, IV_BYTES)));
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
