package com.hris.attendance.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 审批回调相关 DTO（域：考勤，分层：DTO/接口契约）。承载工作流引擎审批结果回调的请求与响应结构。
 */
public final class ApprovalDtos {

    private ApprovalDtos() {
    }

    /** 审批结果回调请求。 */
    public record ApprovalCallbackRequest(
            @NotBlank(message = "业务类型不能为空") String bizType,
            @NotBlank(message = "业务单号不能为空") String bizKey,
            @NotNull(message = "审批结果不能为空") Boolean approved,
            String procInstId,
            String remark) {
    }

    /** 审批回调响应。changed=false 表示单据已处终态，本次为重复回调。 */
    public record ApprovalCallbackResponse(String bizType, String bizKey, String status,
                                           boolean changed) {
    }
}
