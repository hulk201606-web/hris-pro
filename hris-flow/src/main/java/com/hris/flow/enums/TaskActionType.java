package com.hris.flow.enums;

/**
 * 审批动作类型（flow_ext_task_action.action，docs/04 §5）。
 */
public enum TaskActionType {

    /** 同意 */
    APPROVE,
    /** 驳回（终止回发起人，MVP 简化策略） */
    REJECT,
    /** 转办（任务所有权转移，留痕） */
    TRANSFER,
    /** 加签（MVP：追加候选人并行加签） */
    ADD_SIGN,
    /** 撤回（发起人撤回，仅无已处理节点时允许） */
    WITHDRAW;

    public static TaskActionType from(String value) {
        for (TaskActionType t : values()) {
            if (t.name().equalsIgnoreCase(value)) {
                return t;
            }
        }
        return null;
    }
}
