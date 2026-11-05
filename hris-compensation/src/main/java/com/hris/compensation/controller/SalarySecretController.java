package com.hris.compensation.controller;

import com.hris.common.api.R;
import com.hris.common.audit.AuditLog;
import com.hris.common.security.RequirePerm;
import com.hris.compensation.dto.SalarySecretDtos.EmployeePaySecretResponse;
import com.hris.compensation.dto.SalarySecretDtos.PayrollDetailSecretResponse;
import com.hris.compensation.dto.SalarySecretDtos.PayslipSecretResponse;
import com.hris.compensation.dto.SalarySecretDtos.TaxDeductionSecretResponse;
import com.hris.compensation.service.SalarySecretService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 薪酬密域专用读端点（OPEN-DECISIONS Phase 2 薪酬密域，CMP_SALARY_FORBIDDEN 下沉）。
 *
 * <p>双层语义：控制器挂实体通用读权限（如 cmp:payslip:read，无权限被切面挡返 20403）；
 * 服务层再校验密域权限 cmp:salary:read，缺失抛 CMP_SALARY_FORBIDDEN(54001)，
 * 让「可读工资单但不可见金额」与「完全无权限」有可区分的错误码。</p>
 */
@RestController
@RequestMapping("/api/v1/cmp/salary-secret")
public class SalarySecretController {

    private final SalarySecretService service;

    public SalarySecretController(SalarySecretService service) {
        this.service = service;
    }

    @RequirePerm("cmp:payslip:read")
    @AuditLog(value = "查看薪酬密域-工资单", module = "cmp")
    @GetMapping("/payslips/{id}")
    public R<PayslipSecretResponse> payslip(@PathVariable Long id) {
        return R.ok(service.payslip(id));
    }

    @RequirePerm("cmp:payroll-detail:read")
    @AuditLog(value = "查看薪酬密域-算薪明细", module = "cmp")
    @GetMapping("/payroll-details/{id}")
    public R<PayrollDetailSecretResponse> payrollDetail(@PathVariable Long id) {
        return R.ok(service.payrollDetail(id));
    }

    @RequirePerm("cmp:employee-pay:read")
    @AuditLog(value = "查看薪酬密域-员工薪酬", module = "cmp")
    @GetMapping("/employee-pays/{id}")
    public R<EmployeePaySecretResponse> employeePay(@PathVariable Long id) {
        return R.ok(service.employeePay(id));
    }

    @RequirePerm("cmp:tax-deduction:read")
    @AuditLog(value = "查看薪酬密域-专项扣除", module = "cmp")
    @GetMapping("/tax-deductions/{id}")
    public R<TaxDeductionSecretResponse> taxDeduction(@PathVariable Long id) {
        return R.ok(service.taxDeduction(id));
    }
}
