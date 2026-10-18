package com.hris.compensation.dto;

import java.time.LocalDateTime;

/**
 * 薪酬密域 DTO（CMP_SALARY_FORBIDDEN 下沉，OPEN-DECISIONS Phase 2 薪酬密域）。
 *
 * <p>密文字段（*Cipher）不再随通用 CRUD 响应暴露，仅掩码可读；
 * 密文统一经专用端点读取，权限点 cmp:salary:read，缺权限抛 CMP_SALARY_FORBIDDEN(54001)。</p>
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
