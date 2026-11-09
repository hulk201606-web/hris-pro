package com.hris.attendance.service;

import com.hris.attendance.entity.LeaveType;

import java.math.BigDecimal;

/**
 * LeaveQuotaService 的假种规则解析工具（ATT-F-012/013/015）：从假种 rules 文本提取结转上限。
 *
 * <p>从 LeaveQuotaService 拆分而来（单文件 ≤300 行门禁），纯函数无状态。
 * MVP 不做完整 JSON 解析，仅按 "carryOverMax":数字 文本匹配，缺失时用
 * {@link LeaveQuotaService#MAX_CARRY_OVER_DAYS} 兜底。</p>
 */
final class LeaveRuleSupport {

    private LeaveRuleSupport() {
    }

    /**
     * 读取假种规则中的结转上限 rules.carryOverMax。
     * 缺失或非法时用 MAX_CARRY_OVER_DAYS 兜底（完整规则引擎待二期落地，见 OPEN-DECISIONS）。
     */
    static BigDecimal carryOverLimit(LeaveType type) {
        BigDecimal fallback = LeaveQuotaService.MAX_CARRY_OVER_DAYS;
        String rules = type.getRules();
        if (rules == null || rules.isBlank()) {
            return fallback;
        }
        String key = "\"carryOverMax\"";
        int idx = rules.indexOf(key);
        if (idx < 0) {
            return fallback;
        }
        int colon = rules.indexOf(':', idx + key.length());
        if (colon < 0) {
            return fallback;
        }
        StringBuilder digits = new StringBuilder();
        for (int i = colon + 1; i < rules.length(); i++) {
            char c = rules.charAt(i);
            if (c == '.' || (c >= '0' && c <= '9')) {
                digits.append(c);
            } else if (digits.length() > 0) {
                break;
            }
        }
        if (digits.length() == 0) {
            return fallback;
        }
        try {
            BigDecimal parsed = new BigDecimal(digits.toString());
            return parsed.signum() < 0 ? fallback : parsed;
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
