package com.hris.attendance.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 审批回调 DTO（flow 服务回调 /internal/att/approval-callback）。
 *
 * <p>biz_type 取 ATT_LEAVE / ATT_OT / ATT_TRIP / ATT_FIX；回调按 biz_key 幂等，
 * 重复回调（网络重试）不改变已终态的单据。</p>
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
