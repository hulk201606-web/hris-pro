package com.hris.flow.strategy;

import java.util.Locale;
import java.util.Optional;

/**
 * 会签策略类型（FLW-F-008）：BPMN 用户任务扩展属性 {@code strategy} 的取值。
 *
 * <ul>
 *   <li>{@code PERCENT}：比例通过，同意数/总数 &gt;= percentThreshold（缺省 60）即节点通过。</li>
 *   <li>{@code VETO}：一票否决，任一否决即整体驳回。</li>
 * </ul>
 */
public enum ApprovalStrategyType {

    PERCENT,
    VETO;

    /** 解析策略名（忽略大小写与首尾空白），未知取值返回空。 */
    public static Optional<ApprovalStrategyType> parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        String v = raw.trim().toUpperCase(Locale.ROOT);
        for (ApprovalStrategyType t : values()) {
            if (t.name().equals(v)) {
                return Optional.of(t);
            }
        }
        return Optional.empty();
    }
}
