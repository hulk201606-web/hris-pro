package com.hris.compensation.dto;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 员工薪资档案相关 DTO（域：薪酬，分层：DTO/接口契约，密域）。定薪记录的创建/更新/响应结构，密文字段仅授权视图可见明文。
 */
public final class EmployeePayDtos {

    private EmployeePayDtos() {
    }

    /** 创建请求。 */
    public record EmployeePayCreateRequest(@NotNull(message = "empId 不能为空") Long empId, @NotNull(message = "payGroupId 不能为空") Long payGroupId, Long payGradeId, @NotNull(message = "effectiveFrom 不能为空") LocalDate effectiveFrom, LocalDate effectiveTo, String salaryAmountCipher, String salaryAmountMask, String bankAccountCipher, String bankAccountMask) {
    }

    /** 更新请求。 */
    public record EmployeePayUpdateRequest(Long empId, Long payGroupId, Long payGradeId, LocalDate effectiveFrom, LocalDate effectiveTo, String salaryAmountCipher, String salaryAmountMask, String bankAccountCipher, String bankAccountMask) {
    }

    /** 响应。 */
    /**
     * 响应。密域字段 salaryAmountCipher/bankAccountCipher 不在通用响应暴露（薪酬密域下沉，见 SalarySecretDtos）；
     * 仅回掩码，密文经 GET /api/v1/cmp/salary-secret/employee-pays/{id} 专用端点读取。
     */
    public record EmployeePayResponse(Long id, Long empId, Long payGroupId, Long payGradeId, LocalDate effectiveFrom, LocalDate effectiveTo, String salaryAmountMask, String bankAccountMask, LocalDateTime createdAt, LocalDateTime updatedAt) {
    }
}
