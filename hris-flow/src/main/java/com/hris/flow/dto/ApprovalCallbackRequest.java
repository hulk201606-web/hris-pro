package com.hris.flow.dto;

/**
 * 审批回调请求（flow → 业务域，docs/19 §8.7-1 审批回调闭环）。
 *
 * <p>三个域的回调端点共用此结构：bizType/bizKey/approved 为必填语义字段；
 * comment 为审批意见（att 域历史契约字段名为 remark，故双字段同值兼容）；
 * tenantId 供业务域内部端点显式还原租户上下文（内部调用无 JWT，参考 PermInternalController）。</p>
 */
public record ApprovalCallbackRequest(
        String bizType,
        String bizKey,
        Boolean approved,
        String comment,
        String remark,
        String procInstId,
        Long tenantId) {

    public static ApprovalCallbackRequest of(String bizType, String bizKey, boolean approved,
                                             String outcome, String comment, String procInstId,
                                             Long tenantId) {
        return new ApprovalCallbackRequest(bizType, bizKey, approved, comment, comment,
                procInstId, tenantId);
    }
}
