package com.hris.employee.util;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 员工关键字段变更 diff（EMP-F-020 / 批11#21）。
 *
 * <p>输出结构固定为 {@code {field: {before, after}}}，只保留真正变化的字段；
 * 证件号/手机号/银行账号等敏感字段在写入审计前脱敏，明文绝不出现在审计明细里。
 * 纯函数，便于单测覆盖「变更生成」与「脱敏」两条口径。</p>
 */
public final class EmpDiffUtil {

    /** 走证件号掩码的字段名（兼容下划线与驼峰）。 */
    private static final Set<String> CERT_FIELDS = Set.of("certNo", "cert_no", "cert_no_cipher");
    /** 走手机号掩码的字段名。 */
    private static final Set<String> MOBILE_FIELDS = Set.of("mobile", "mobile_cipher", "phone",
            "mobileMask", "emergencyPhone", "emergency_phone");
    /** 银行卡类字段：仅保留后 4 位。 */
    private static final Set<String> BANK_FIELDS = Set.of("bankAccount", "bank_account", "bank_account_cipher");

    private EmpDiffUtil() {
    }

    /**
     * 计算字段级 diff。
     *
     * @param before 变更前字段快照
     * @param after  变更后字段快照（null=未提供，不参与比较）
     * @return 有序 diff；无变化返回空 Map
     */
    public static Map<String, Object> diff(Map<String, Object> before, Map<String, Object> after) {
        Map<String, Object> out = new LinkedHashMap<>();
        if (after == null || after.isEmpty()) {
            return out;
        }
        for (Map.Entry<String, Object> e : after.entrySet()) {
            String field = e.getKey();
            Object oldValue = before == null ? null : before.get(field);
            Object newValue = e.getValue();
            if (Objects.equals(oldValue, newValue)) {
                continue;
            }
            Map<String, Object> change = new LinkedHashMap<>();
            change.put("before", mask(field, oldValue));
            change.put("after", mask(field, newValue));
            out.put(field, change);
        }
        return out;
    }

    /** 按字段名脱敏：证件/手机/银行卡走掩码，其余原样。 */
    public static Object mask(String field, Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value);
        if (CERT_FIELDS.contains(field)) {
            return MaskUtil.maskCertNo(text);
        }
        if (MOBILE_FIELDS.contains(field)) {
            return MaskUtil.maskMobile(text);
        }
        if (BANK_FIELDS.contains(field)) {
            return maskBankAccount(text);
        }
        return value;
    }

    /** 银行账号脱敏：短号全掩码，长号仅保留后 4 位。 */
    public static String maskBankAccount(String account) {
        if (account == null || account.isBlank()) {
            return null;
        }
        String v = account.trim();
        if (v.length() <= 4) {
            return "*".repeat(v.length());
        }
        return "*".repeat(Math.min(12, v.length() - 4)) + v.substring(v.length() - 4);
    }
}
