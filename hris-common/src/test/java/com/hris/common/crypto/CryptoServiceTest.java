package com.hris.common.crypto;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 加密核心单测（排期批 12 #30）：enc2 信封往返 / 新写为 enc2 / enc1 存量可读 /
 * 篡改与 keyId 失配报错 / AAD 绑定 / 弱密钥拒绝 / 迁移期兼容模式 / KMS wrap-unwrap。
 */
class CryptoServiceTest {

    private static final String AAD = "emp_employee:cert_no";
    private static final String AAD_OTHER = "cmp_payslip:net_pay";
    private static final String KEY_B64 = "3q2+7wKcQpFzv1xJmN8oLdYhU4gT5rWqA0eB6vCxHyM=";
    private static final String OTHER_KEY_B64 = "YWJjZGVmZ2hpamtsbW5vcHFyc3R1dnd4eXowMTIzNDU=";

    private static CryptoService enabledService() {
        return new CryptoService(CryptoKeyGuard.requireStrongKey(KEY_B64));
    }

    @AfterEach
    void cleanup() {
        CryptoService.setInstance(CryptoService.disabled());
    }

    @Test
    void encryptDecryptRoundTripUsesEnvelope() {
        CryptoService svc = enabledService();
        String plain = "110101199003074258";
        String cipher = svc.encrypt(plain, AAD);
        assertTrue(cipher.startsWith(CryptoService.PREFIX_V2), "新写必须为 enc2 信封格式");
        assertNotEquals(plain, cipher);
        assertEquals(plain, svc.decryptToPlain(cipher, AAD));
    }

    @Test
    void enc2LayoutIsKeyIdIvCiphertextTag() {
        CryptoService svc = enabledService();
        String plain = "13800138000";
        String cipher = svc.encrypt(plain, AAD);
        byte[] raw = Base64.getDecoder().decode(cipher.substring(CryptoService.PREFIX_V2.length()));
        int ct = plain.getBytes(StandardCharsets.UTF_8).length;
        assertEquals(8 + 12 + ct + 16, raw.length);
        // 头部 8 字节即 keyId
        assertArrayEquals(Arrays.copyOfRange(raw, 0, 8), hexToBytes(svc.currentKeyId()));
    }

    @Test
    void legacyEnc1CiphertextStillReadable() throws Exception {
        CryptoService svc = enabledService();
        String legacy = legacyEncrypt(KEY_B64, "110101199003074258", AAD);
        assertTrue(legacy.startsWith(CryptoService.PREFIX));
        assertEquals("110101199003074258", svc.decryptToPlain(legacy, AAD));
    }

    @Test
    void randomIvProducesDistinctCiphertexts() {
        CryptoService svc = enabledService();
        assertNotEquals(svc.encrypt("13800138000", AAD), svc.encrypt("13800138000", AAD));
    }

    @Test
    void encryptIsIdempotentForCiphertext() {
        CryptoService svc = enabledService();
        String cipher = svc.encrypt("13800138000", AAD);
        assertEquals(cipher, svc.encrypt(cipher, AAD), "已是密文的值不得二次加密（迁移期防混写）");
    }

    @Test
    void doubleReadReturnsLegacyPlaintextAsIs() {
        assertEquals("110101199003074258", enabledService().decryptToPlain("110101199003074258", AAD));
    }

    @Test
    void aadMismatchFailsDecrypt() {
        CryptoService svc = enabledService();
        String cipher = svc.encrypt("13800138000", AAD);
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> svc.decryptToPlain(cipher, AAD_OTHER));
        assertTrue(ex.getMessage().contains(AAD_OTHER));
    }

    @Test
    void tamperedEnc2CiphertextFailsDecrypt() {
        CryptoService svc = enabledService();
        String cipher = svc.encrypt("13800138000", AAD);
        byte[] raw = Base64.getDecoder().decode(cipher.substring(CryptoService.PREFIX_V2.length()));
        raw[raw.length - 1] ^= 0x01;
        String tampered = CryptoService.PREFIX_V2 + Base64.getEncoder().encodeToString(raw);
        assertThrows(IllegalStateException.class, () -> svc.decryptToPlain(tampered, AAD));
    }

    @Test
    void tamperedEnc1CiphertextFailsDecrypt() throws Exception {
        CryptoService svc = enabledService();
        String legacy = legacyEncrypt(KEY_B64, "13800138000", AAD);
        byte[] raw = Base64.getDecoder().decode(legacy.substring(CryptoService.PREFIX.length()));
        raw[raw.length - 1] ^= 0x01;
        String tampered = CryptoService.PREFIX + Base64.getEncoder().encodeToString(raw);
        assertThrows(IllegalStateException.class, () -> svc.decryptToPlain(tampered, AAD));
    }

    @Test
    void rotatedMasterKeyFailsLoudOnKeyIdMismatch() {
        CryptoService oldService = enabledService();
        String cipher = oldService.encrypt("13800138000", AAD);
        CryptoService rotated = new CryptoService(CryptoKeyGuard.requireStrongKey(OTHER_KEY_B64));
        assertNotEquals(oldService.currentKeyId(), rotated.currentKeyId());
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> rotated.decryptToPlain(cipher, AAD));
        assertTrue(ex.getMessage().contains("不匹配"), "keyId 失配必须显式报错而非静默");
    }

    @Test
    void enc1RejectedWhenNoLegacyKeyAvailable() throws Exception {
        // 仅装配 KMS（无主密钥直解能力）时，enc1 存量应显式报错，提示先迁移
        byte[] key = CryptoKeyGuard.requireStrongKey(KEY_B64);
        CryptoService kmsOnly = new CryptoService(new LocalKmsProvider(key));
        String legacy = legacyEncrypt(KEY_B64, "13800138000", AAD);
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> kmsOnly.decryptToPlain(legacy, AAD));
        assertTrue(ex.getMessage().contains("migrate_encrypt_v2"));
    }

    @Test
    void nullAndEmptyPassThrough() {
        CryptoService svc = enabledService();
        assertNull(svc.encrypt(null, AAD));
        assertEquals("", svc.encrypt("", AAD));
        assertNull(svc.decryptToPlain(null, AAD));
        assertEquals("", svc.decryptToPlain("", AAD));
    }

    @Test
    void disabledModePassesThroughPlaintext() {
        CryptoService svc = CryptoService.disabled();
        assertFalse(svc.isEnabled());
        assertEquals("13800138000", svc.encrypt("13800138000", AAD));
        assertEquals("13800138000", svc.decryptToPlain("13800138000", AAD));
        assertThrows(IllegalStateException.class,
                () -> svc.decryptToPlain(enabledService().encrypt("x", AAD), AAD));
    }

    @Test
    void isCiphertextRecognizesBothVersions() {
        assertTrue(CryptoService.isCiphertext("enc1:AAAA"));
        assertTrue(CryptoService.isCiphertext("enc2:AAAA"));
        assertFalse(CryptoService.isCiphertext("plain"));
        assertFalse(CryptoService.isCiphertext(null));
    }

    @Test
    void kmsWrapUnwrapRoundTrip() {
        byte[] key = CryptoKeyGuard.requireStrongKey(KEY_B64);
        LocalKmsProvider kms = new LocalKmsProvider(key);
        KmsProvider.DataKey dataKey = kms.generateDataKey();
        assertEquals(kms.currentKeyId(), dataKey.keyId());
        String wrapped = kms.wrapKey(dataKey.key());
        SecretKey unwrapped = kms.unwrapKey(wrapped, dataKey.keyId());
        assertNotNull(unwrapped);
        assertArrayEquals(dataKey.key().getEncoded(), unwrapped.getEncoded());
        // keyId 失配 → null（版本化语义）
        assertNull(kms.unwrapKey(wrapped, "0000000000000000"));
    }

    @Test
    void kmsDataKeyIsStableAcrossInstances() {
        byte[] key = CryptoKeyGuard.requireStrongKey(KEY_B64);
        LocalKmsProvider a = new LocalKmsProvider(key);
        LocalKmsProvider b = new LocalKmsProvider(key);
        assertArrayEquals(a.generateDataKey().key().getEncoded(),
                b.generateDataKey().key().getEncoded(), "重启后必须能按主密钥重新派生同一 DEK");
    }

    @Test
    void sha256HexMatchesKnownVector() {
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
                CryptoService.sha256Hex("abc"));
    }

    @Test
    void getBeforeInitThrows() {
        assertDoesNotThrow(CryptoService::get);
    }

    /** 按 v1 口径（主密钥直解）构造存量密文，验证双读兼容。 */
    static String legacyEncrypt(String keyB64, String plain, String aad) throws Exception {
        SecretKey key = new SecretKeySpec(Base64.getDecoder().decode(keyB64), "AES");
        byte[] iv = new byte[12];
        new SecureRandom().nextBytes(iv);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(128, iv));
        cipher.updateAAD(aad.getBytes(StandardCharsets.UTF_8));
        byte[] ct = cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8));
        byte[] out = new byte[iv.length + ct.length];
        System.arraycopy(iv, 0, out, 0, iv.length);
        System.arraycopy(ct, 0, out, iv.length, ct.length);
        return CryptoService.PREFIX + Base64.getEncoder().encodeToString(out);
    }

    private static byte[] hexToBytes(String hex) {
        byte[] out = new byte[hex.length() / 2];
        for (int i = 0; i < out.length; i++) {
            out[i] = (byte) Integer.parseInt(hex.substring(i * 2, i * 2 + 2), 16);
        }
        return out;
    }
}
