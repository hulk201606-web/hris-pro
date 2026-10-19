package com.hris.compensation.dto;

import java.time.LocalDateTime;

/**
 * 密域读取响应 DTO（域：薪酬，分层：DTO/密域专用）。SalarySecretService 专用响应，同时携带 cipher 明文与 mask 掩码，调用方须通过 cmp:salary:read 校验。
 */
public final class SalarySecretDtos {

    private SalarySecretDtos() {
    }

    /** 工资单密域响应（cmp_payslip.net_pay_cipher）。 */
    public record PayslipSecretResponse(Long id, Long empId, String netPayCipher, String netPayMask,
                                        LocalDateTime updatedAt) {
    }

    /** 核算明细密域响应（cmp_payroll_detail.amount/net_amount_cipher）。 */
    public record PayrollDetailSecretResponse(Long id, Long empId, String amountCipher, String amountMask,
                                              String netAmountCipher, String netAmountMask,
                                              LocalDateTime updatedAt) {
    }

    /** 员工定薪密域响应（cmp_employee_pay.salary_amount/bank_account_cipher）。 */
    public record EmployeePaySecretResponse(Long id, Long empId, String salaryAmountCipher,
                                            String salaryAmountMask, String bankAccountCipher,
                                            String bankAccountMask, LocalDateTime updatedAt) {
    }

    /** 专项附加扣除密域响应（cmp_tax_deduction.deduction_amount_cipher）。 */
    public record TaxDeductionSecretResponse(Long id, Long empId, String deductionAmountCipher,
                                             String deductionAmountMask, LocalDateTime updatedAt) {
    }
}
