package com.hris.attendance.util;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;

/**
 * 编码工具类（域：考勤，分层：工具/Util）。业务单据编号生成（请假单号/加班单号等）。
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

    /** 业务单号：前缀 + 雪花 ID（uk(tenant_id, biz_key) 兜底幂等）。 */
    public static String bizKey(String bizType) {
        return normalize(bizType) + ":" + IdWorker.getId();
    }
}
