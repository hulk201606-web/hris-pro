package com.hris.flow.enums;

/**
 * 审批动作类型（flow_ext_task_action.action，docs/04 §5）。
 */
public enum TaskActionType {

    /** 同意 */
    APPROVE,
    /** 驳回（缺省=退回发起人重提；terminate=true 时=终止作废） */
    REJECT,
    /** 转办（任务所有权转移，留痕） */
    TRANSFER,
    /** 认领无主任务（操作者自领，留痕） */
    CLAIM,
    /** 指派（管理动作：强制指给目标用户，留痕） */
    ASSIGN,
    /** 加签（MVP：追加候选人并行加签） */
    ADD_SIGN,
    /** 撤回（发起人撤回，仅无已处理节点时允许） */
    WITHDRAW,
    /** 超时升级（内部端点：超期任务改派升级目标，docs/19 §10.5-C FLW-F-010） */
    ESCALATE;

    public static TaskActionType from(String value) {
        for (TaskActionType t : values()) {
            if (t.name().equalsIgnoreCase(value)) {
                return t;
            }
        }
        return null;
    }
}
