package com.hris.employee.util;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;

/**
 * 业务编码生成工具（EMP-F-002 / 生命周期单号 / 事务流水号）。标识符字段统一 trim+lower 规范化（docs/12 §2 第 11 条）。
 */
public final class CodeUtil {

    private CodeUtil() {
    }

    /** 归一化：去除首尾空白并转小写。 */
    public static String normalize(String code) {
        return code == null ? null : code.trim().toLowerCase();
    }

    /** 自动生成编码：前缀 + 雪花 ID。 */
    public static String next(String prefix) {
        return normalize(prefix) + IdWorker.getId();
    }

    /** 工号生成（MVP）：租户级前缀 + 雪花后缀，唯一性由 uk(tenant_id, emp_no) 兜底。 */
    public static String nextEmpNo(String prefix) {
        String p = normalize(prefix);
        if (p == null || p.isBlank()) {
            p = "e";
        }
        return p + IdWorker.getId();
    }
}
