package com.hris.flow.dto;

import java.util.List;

/**
 * 任务操作请求（同意/驳回/转办/加签，FLW-F-007）。
 * 撤回无请求体（发起人身份由当前登录用户判定）。
 */
public record TaskActionRequest(
        /** 审批意见 */
        String comment,
        /** 附件列表（fileId 引用） */
        List<Long> attachments,
        /** 转办/加签目标用户 ID */
        Long targetUserId,
        /** 驳回语义开关（仅 reject 生效）：缺省/false=退回发起人重提（实例保持 RUNNING）；
         * true=终止（等价作废，删除实例并按 REJECTED 终态回调业务域） */
        Boolean terminate,
        /** 动态表单值（键=字段名，随审批动作落 flow_ext_task_action.form_values） */
        java.util.Map<String, Object> formValues) {

    /** 便捷构造：不带动态表单值（既有调用与测试兼容）。 */
    public TaskActionRequest(String comment, java.util.List<Long> attachments,
                             Long targetUserId, Boolean terminate) {
        this(comment, attachments, targetUserId, terminate, null);
    }
}
