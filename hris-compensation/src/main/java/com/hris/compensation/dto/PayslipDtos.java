package com.hris.compensation.dto;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;

/**
 * 工资单相关 DTO（域：薪酬，分层：DTO/接口契约，密域）。工资单查询/发送结构，实发金额密文/掩码双列。
 */
public final class PayslipDtos {

    private PayslipDtos() {
    }

    /** 创建请求。 */
    public record PayslipCreateRequest(@NotNull(message = "runId 不能为空") Long runId, @NotNull(message = "empId 不能为空") Long empId, String netPayCipher, String netPayMask, @NotNull(message = "sendStatus 不能为空") String sendStatus, LocalDateTime sentAt) {
    }

    /** 更新请求。 */
    public record PayslipUpdateRequest(Long runId, Long empId, String netPayCipher, String netPayMask, String sendStatus, LocalDateTime sentAt) {
    }

    /** 响应。 */
    /**
     * 响应。密域字段 netPayCipher 不在通用响应暴露（薪酬密域下沉，见 SalarySecretDtos）；
     * 仅回掩码，密文经 GET /api/v1/cmp/salary-secret/payslips/{id} 专用端点读取。
     */
    public record PayslipResponse(Long id, Long runId, Long empId, String netPayMask, String sendStatus, LocalDateTime sentAt, LocalDateTime createdAt, LocalDateTime updatedAt) {
    }

    /**
     * 员工自助工资条响应：仅掩码字段，绝不携带 cipher；
     * periodCode 取自算薪批次，grossMask 取自该员工当期核算明细的应发掩码。
     */
    public record PayslipMeResponse(Long id, Long runId, String periodCode, String grossMask,
                                    String netPayMask, String sendStatus, LocalDateTime sentAt) {
    }
}
