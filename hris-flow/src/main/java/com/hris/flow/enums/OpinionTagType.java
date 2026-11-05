package com.hris.flow.enums;

import com.hris.common.exception.BizException;
import com.hris.common.exception.ErrorCode;

import java.util.Locale;
import java.util.Optional;

/**
 * 结构化审批意见标签（FLW-F-020）。
 *
 * <ul>
 *   <li>{@code RECHECK_DATA}：要求复核数据；</li>
 *   <li>{@code NEED_MORE_INFO}：要求补充材料；</li>
 *   <li>{@code COMPLIANT}：符合规定（无异议通过）；</li>
 *   <li>{@code OTHER}：其他，需配合自由文本说明。</li>
 * </ul>
 */
public enum OpinionTagType {

    RECHECK_DATA,
    NEED_MORE_INFO,
    COMPLIANT,
    OTHER;

    /** 解析标签（忽略大小写与首尾空白），未知取值返回空。 */
    public static Optional<OpinionTagType> parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        String v = raw.trim().toUpperCase(Locale.ROOT);
        for (OpinionTagType t : values()) {
            if (t.name().equals(v)) {
                return Optional.of(t);
            }
        }
        return Optional.empty();
    }

    /** 解析标签，非法取值抛 10001。 */
    public static OpinionTagType require(String raw) {
        return parse(raw).orElseThrow(() -> new BizException(ErrorCode.PARAM_INVALID,
                "意见标签取值非法，允许: RECHECK_DATA/NEED_MORE_INFO/COMPLIANT/OTHER"));
    }
}
