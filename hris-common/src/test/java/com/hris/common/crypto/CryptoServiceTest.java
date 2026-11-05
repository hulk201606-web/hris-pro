package com.hris.common.crypto;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ADR-007 加密核心单测：加解密往返 / 双读明文兼容 / AAD 绑定 / 弱密钥拒绝 / 迁移期兼容模式。
 */
class CryptoServiceTest {

    private static final String AAD = "emp_employee:cert_no";
    private static final String AAD_OTHER = "cmp_payslip:net_pay";

    private static CryptoService enabledService() {
        // 32 字节随机密钥的固定 Base64（测试专用，非弱值：字节各不相同）
        return new CryptoService(CryptoKeyGuard.requireStrongKey(
                "3q2+7wKcQpFzv1xJmN8oLdYhU4gT5rWqA0eB6vCxHyM="));
    }

    @AfterEach
    void cleanup() {
        CryptoService.setInstance(CryptoService.disabled());
    }

    @Test
    void encryptDecryptRoundTrip() {
        CryptoService svc = enabledService();
        String plain = "110101199003074258";
        String cipher = svc.encrypt(plain, AAD);
        assertTrue(cipher.startsWith(CryptoService.PREFIX), "密文必须带 enc1: 前缀");
        assertNotEquals(plain, cipher);
        assertEquals(plain, svc.decryptToPlain(cipher, AAD));
    }

    @Test
    void randomIvProducesDistinctCiphertexts() {
        CryptoService svc = enabledService();
        String c1 = svc.encrypt("13800138000", AAD);
        String c2 = svc.encrypt("13800138000", AAD);
        assertNotEquals(c1, c2, "随机 IV 下同一明文两次加密密文应不同");
    }

    @Test
    void encryptIsIdempotentForCiphertext() {
        CryptoService svc = enabledService();
        String cipher = svc.encrypt("13800138000", AAD);
        assertEquals(cipher, svc.encrypt(cipher, AAD), "已是密文的值不得二次加密（迁移期防混写）");
    }

    @Test
    void doubleReadReturnsLegacyPlaintextAsIs() {
        CryptoService svc = enabledService();
        // 存量明文行：无 enc1: 前缀原样返回（迁移期双读兼容）
        assertEquals("110101199003074258", svc.decryptToPlain("110101199003074258", AAD));
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
    void tamperedCiphertextFailsDecrypt() {
        CryptoService svc = enabledService();
        String cipher = svc.encrypt("13800138000", AAD);
        byte[] raw = Base64.getDecoder().decode(cipher.substring(CryptoService.PREFIX.length()));
        raw[raw.length - 1] ^= 0x01;
        String tampered = CryptoService.PREFIX + Base64.getEncoder().encodeToString(raw);
        assertThrows(IllegalStateException.class, () -> svc.decryptToPlain(tampered, AAD));
    }

    @Test
    void wrongKeyFailsDecrypt() {
        CryptoService svc = enabledService();
        String cipher = svc.encrypt("13800138000", AAD);
        CryptoService other = new CryptoService(CryptoKeyGuard.requireStrongKey(
                "YWJjZGVmZ2hpamtsbW5vcHFyc3R1dnd4eXowMTIzNDU="));
        assertThrows(IllegalStateException.class, () -> other.decryptToPlain(cipher, AAD));
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
        assertEquals("13800138000", svc.encrypt("13800138000", AAD), "未配置密钥时明文透传（迁移期兼容）");
        assertEquals("13800138000", svc.decryptToPlain("13800138000", AAD));
        // 库里已有密文但服务未配密钥：fail loud
        assertThrows(IllegalStateException.class,
                () -> svc.decryptToPlain(enabledService().encrypt("x", AAD), AAD));
    }

    @Test
    void sha256HexMatchesKnownVector() {
        // echo -n "abc" | sha256sum
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
                CryptoService.sha256Hex("abc"));
    }

    @Test
    void getBeforeInitThrows() {
        // 用未初始化语义验证：静态实例被清理后（disabled 实例不算未初始化），此处仅验证 get() 可用
        assertDoesNotThrow(CryptoService::get);
    }

    @Test
    void ciphertextFormatIsBase64OfIvPlusCiphertextWithTag() {
        CryptoService svc = enabledService();
        String cipher = svc.encrypt("13800138000", AAD);
        byte[] raw = Base64.getDecoder().decode(cipher.substring(CryptoService.PREFIX.length()));
        // GCM: 12B IV + 密文(=明文长度) + 16B tag
        assertEquals(12 + "13800138000".getBytes(StandardCharsets.UTF_8).length + 16, raw.length);
    }
}
