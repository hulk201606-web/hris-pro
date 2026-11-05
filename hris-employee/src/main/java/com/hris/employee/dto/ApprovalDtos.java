package com.hris.employee.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 生命周期单审批回调契约（flow → hris-employee，docs/19 §8.7-1 审批回调闭环）。
 */
public final class ApprovalDtos {

    private ApprovalDtos() {
    }

    /** 审批结果回调请求（bizType 为 EMP_* 前缀，仅供审计；定位单据以 bizKey 为准）。 */
    public record ApprovalCallbackRequest(
            String bizType,
            @NotBlank(message = "业务单号不能为空") String bizKey,
            @NotNull(message = "审批结果不能为空") Boolean approved,
            String comment,
            String procInstId,
            Long tenantId) {
    }

    /** 审批回调响应。changed=false 表示单据已处终态，本次为重复回调（幂等成功）。 */
    public record ApprovalCallbackResponse(boolean changed, String state) {
    }
}
