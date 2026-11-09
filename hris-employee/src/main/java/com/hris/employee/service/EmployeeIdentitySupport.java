package com.hris.employee.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hris.common.exception.BizException;
import com.hris.common.exception.ErrorCode;
import com.hris.employee.entity.Employee;
import com.hris.employee.mapper.EmployeeMapper;
import com.hris.employee.util.MaskUtil;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 员工身份字段（证件/手机号）的密文-哈希-掩码三列写入与查重哈希。
 *
 * <p>从 EmployeeService 拆分而来（单文件 ≤300 行门禁）。证件号明文只在内存中比较，
 * 落审计前由 EmpDiffUtil 脱敏（EMP-F-001/020）。</p>
 */
final class EmployeeIdentitySupport {

    private EmployeeIdentitySupport() {
    }

    /** 取员工实体，不存在抛 NOT_FOUND。 */
    static Employee require(EmployeeMapper employeeMapper, Long id) {
        Employee e = employeeMapper.selectById(id);
        if (e == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "员工不存在");
        }
        return e;
    }

    /** 证件号哈希：SHA-256 小写十六进制（等值查重键，生产者路径唯一化）。 */
    static String hashCertNo(String certNo) {
        if (certNo == null || certNo.isBlank()) {
            return null;
        }
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(certNo.trim().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    /** 更新敏感列的密文/哈希/掩码（审计由 changeAuditService 统一按 diff 落库）。 */
    static void applySensitiveChange(Employee e, String field, String currentCipher, String newPlain) {
        if (newPlain == null || newPlain.isBlank()) {
            return;
        }
        if (newPlain.equals(currentCipher)) {
            return;
        }
        if ("cert_no".equals(field)) {
            e.setCertNoCipher(newPlain);
            e.setCertNoHash(hashCertNo(newPlain));
            e.setCertNoMask(MaskUtil.maskCertNo(newPlain));
        } else {
            e.setMobileCipher(newPlain);
            e.setMobileMask(MaskUtil.maskMobile(newPlain));
        }
    }

    /** 关键字段快照（diff 采集用）。 */
    static Map<String, Object> snapshot(Employee e) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("name", e.getName());
        m.put("cert_no", e.getCertNoCipher());
        m.put("cert_type", e.getCertType());
        m.put("mobile", e.getMobileCipher());
        m.put("email", e.getEmail());
        m.put("address", e.getAddress());
        return m;
    }

    /** 回调载荷取值：null 安全转字符串。 */
    static String text(Object v) {
        return v == null ? null : String.valueOf(v);
    }
}
