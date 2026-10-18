package com.hris.flow.enums;

/**
 * 事件出站状态（flow_ext_event_outbox.state）。
 */
public enum OutboxState {

    /** 待发送 */
    PENDING,
    /** 已发送 */
    SENT,
    /** 死信（超重试上限） */
    DEAD;

    public static OutboxState from(String value) {
        for (OutboxState s : values()) {
            if (s.name().equalsIgnoreCase(value)) {
                return s;
            }
        }
        return null;
    }
}
