package com.hris.attendance.util;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;

/**
 * 业务编码生成工具（请假/加班/出差/补卡单号 + 标识字段规范化）。
 *
 * <p>说明：本模块确有 util 包，理由为四类申请单（leave/overtime/trip/fix）共用同一套
 * 「biz_key 生成 + 编码 trim/lower 规范化」逻辑，若不抽出将在 4 个 service 中重复复制。
 * 与 hris-org / hris-employee 的 util/CodeUtil 约定保持一致（docs/12 §2 第 11 条）。</p>
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
