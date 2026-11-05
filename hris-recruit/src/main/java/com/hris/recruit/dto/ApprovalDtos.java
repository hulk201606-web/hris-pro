package com.hris.recruit.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 招聘域审批回调契约（flow → hris-recruit，docs/19 §9 遗留项四域接入）。
 */
public final class ApprovalDtos {

    private ApprovalDtos() {
    }

    /** 审批结果回调请求（bizType 为 RCS_* 前缀，仅供审计；定位单据以 bizKey 为准）。 */
    public record ApprovalCallbackRequest(
            String bizType,
            @NotBlank(message = "业务单号不能为空") String bizKey,
            @NotNull(message = "审批结果不能为空") Boolean approved,
            String comment,
            String procInstId,
            Long tenantId) {
    }

    /** 审批回调响应。changed=false 表示单据不处于可迁移状态（终态重复回调或无审批载体），幂等成功。 */
    public record ApprovalCallbackResponse(boolean changed, String status) {
    }
}
