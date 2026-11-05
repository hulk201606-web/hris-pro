package com.hris.common.crypto;

import org.apache.ibatis.reflection.SystemMetaObject;
import org.apache.ibatis.type.JdbcType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * TypeHandler 读写单测：写侧加密落参、读侧双读兼容（密文解密 / 存量明文透传）。
 */
class CipherTypeHandlerTest {

    private final CipherTypeHandler handler = new CipherTypeHandler.EmpCertNo();

    @BeforeEach
    void setUp() {
        CryptoService.setInstance(new CryptoService(CryptoKeyGuard.requireStrongKey(
                "3q2+7wKcQpFzv1xJmN8oLdYhU4gT5rWqA0eB6vCxHyM=")));
    }

    @AfterEach
    void tearDown() {
        CryptoService.setInstance(CryptoService.disabled());
    }

    @Test
    void writeEncryptsParameter() throws Exception {
        PreparedStatement ps = mock(PreparedStatement.class);
        handler.setNonNullParameter(ps, 1, "110101199003074258", JdbcType.VARCHAR);
        var captor = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(ps).setString(org.mockito.ArgumentMatchers.eq(1), captor.capture());
        String bound = captor.getValue();
        assertTrue(CryptoService.isCiphertext(bound), "写侧参数必须是 enc1: 密文");
        // AAD 为 表名:逻辑列，可解回原值
        assertEquals("110101199003074258", CryptoService.get().decryptToPlain(bound, "emp_employee:cert_no"));
    }

    @Test
    void readDecryptsCiphertext() throws Exception {
        String cipher = CryptoService.get().encrypt("110101199003074258", "emp_employee:cert_no");
        ResultSet rs = mock(ResultSet.class);
        when(rs.getString("cert_no_cipher")).thenReturn(cipher);
        assertEquals("110101199003074258", handler.getNullableResult(rs, "cert_no_cipher"));
    }

    @Test
    void readPassesThroughLegacyPlaintext() throws Exception {
        ResultSet rs = mock(ResultSet.class);
        when(rs.getString("cert_no_cipher")).thenReturn("110101199003074258");
        assertEquals("110101199003074258", handler.getNullableResult(rs, "cert_no_cipher"),
                "存量明文行（无 enc1: 前缀）读侧原样返回");
    }

    @Test
    void readNullPassesThrough() throws Exception {
        ResultSet rs = mock(ResultSet.class);
        when(rs.getString("cert_no_cipher")).thenReturn(null);
        assertNull(handler.getNullableResult(rs, "cert_no_cipher"));
    }

    @Test
    void eachColumnHasDistinctAad() {
        assertEquals("emp_employee:cert_no", new CipherTypeHandler.EmpCertNo().aad());
        assertEquals("emp_employee:mobile", new CipherTypeHandler.EmpMobile().aad());
        assertEquals("cmp_payslip:net_pay", new CipherTypeHandler.CmpPayslipNetPay().aad());
        // AAD 不同：同明文加密结果互不可解
        String c = CryptoService.get().encrypt("x", "emp_employee:cert_no");
        org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class,
                () -> CryptoService.get().decryptToPlain(c, "emp_employee:mobile"));
    }

    @Test
    void hashFillerBackfillsMissingHash() {
        Map<String, Object> entity = new HashMap<>();
        entity.put("certNoCipher", "110101199003074258");
        entity.put("certNoHash", null);
        var metaObject = SystemMetaObject.forObject(entity);
        CipherHashFiller.fill(metaObject);
        assertEquals(CryptoService.sha256Hex("110101199003074258"), entity.get("certNoHash"));
    }

    @Test
    void hashFillerKeepsServiceSetHash() {
        Map<String, Object> entity = new HashMap<>();
        entity.put("certNoCipher", "110101199003074258");
        entity.put("certNoHash", "already-set");
        var metaObject = SystemMetaObject.forObject(entity);
        CipherHashFiller.fill(metaObject);
        assertEquals("already-set", entity.get("certNoHash"));
    }

    @Test
    void hashFillerSkipsCiphertextValue() {
        String cipher = CryptoService.get().encrypt("x", "emp_employee:cert_no");
        Map<String, Object> entity = new HashMap<>();
        entity.put("certNoCipher", cipher);
        entity.put("certNoHash", null);
        var metaObject = SystemMetaObject.forObject(entity);
        CipherHashFiller.fill(metaObject);
        assertNull(entity.get("certNoHash"), "密文值无法派生明文 hash，必须跳过");
    }
}
