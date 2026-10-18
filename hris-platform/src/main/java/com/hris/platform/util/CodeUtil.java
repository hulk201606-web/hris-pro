package com.hris.platform.util;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;

/**
 * 编码生成工具。标识符类字段统一 trim+lower 规范化（docs/12 §2 第 11 条）。
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
}
