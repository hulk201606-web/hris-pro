package com.hris.common.util;

/**
 * 敏感字段脱敏工具（docs/19 §8.7-3，供无 employee 依赖的服务如 rcs 使用）。
 * 纯函数：手机号保留前 3 后 4，与 hris-employee MaskUtil 算法一致。
 */
public final class MaskUtil {

    private MaskUtil() {
    }

    /** 手机号脱敏：保留前 3 位 + 掩码 + 后 4 位；短串全掩码。 */
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
