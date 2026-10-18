package com.hris.employee.util;

/**
 * 敏感字段脱敏工具（EMP-F-025）。纯函数：证件号保留前 4 后 2，手机号保留前 3 后 4。
 */
public final class MaskUtil {

    private MaskUtil() {
    }

    /** 证件号脱敏：保留前 4 位 + 掩码 + 后 2 位。 */
    public static String maskCertNo(String certNo) {
        if (certNo == null || certNo.isBlank()) {
            return null;
        }
        String v = certNo.trim();
        if (v.length() <= 6) {
            return "*".repeat(v.length());
        }
        return v.substring(0, 4) + "*".repeat(Math.min(8, v.length() - 6)) + v.substring(v.length() - 2);
    }

    /** 手机号脱敏：保留前 3 位 + 掩码 + 后 4 位。 */
    public static String maskMobile(String mobile) {
        if (mobile == null || mobile.isBlank()) {
            return null;
        }
        String v = mobile.trim();
        if (v.length() < 7) {
            return "*".repeat(v.length());
        }
        return v.substring(0, 3) + "****" + v.substring(v.length() - 4);
    }
}
