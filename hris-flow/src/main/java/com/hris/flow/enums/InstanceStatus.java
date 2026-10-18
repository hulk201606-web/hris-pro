package com.hris.flow.enums;

/**
 * 流程实例状态（flow_ext_biz_relation.status）。
 * 与 Flowable 运行态/历史态对齐：RUNNING 运行中，FINISHED 正常结束，TERMINATED 撤回/驳回/作废。
 */
public enum InstanceStatus {

    RUNNING,
    FINISHED,
    TERMINATED;

    public static InstanceStatus from(String value) {
        for (InstanceStatus s : values()) {
            if (s.name().equalsIgnoreCase(value)) {
                return s;
            }
        }
        return null;
    }
}
