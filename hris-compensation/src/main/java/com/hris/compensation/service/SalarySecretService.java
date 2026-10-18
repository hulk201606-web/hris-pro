package com.hris.compensation.service;

import com.hris.common.exception.BizException;
import com.hris.common.exception.ErrorCode;
import com.hris.common.security.AuthContextHolder;
import com.hris.compensation.dto.SalarySecretDtos.EmployeePaySecretResponse;
import com.hris.compensation.dto.SalarySecretDtos.PayrollDetailSecretResponse;
import com.hris.compensation.dto.SalarySecretDtos.PayslipSecretResponse;
import com.hris.compensation.dto.SalarySecretDtos.TaxDeductionSecretResponse;
import com.hris.compensation.entity.EmployeePay;
import com.hris.compensation.entity.PayrollDetail;
import com.hris.compensation.entity.Payslip;
import com.hris.compensation.entity.TaxDeduction;
import com.hris.compensation.mapper.EmployeePayMapper;
import com.hris.compensation.mapper.PayrollDetailMapper;
import com.hris.compensation.mapper.PayslipMapper;
import com.hris.compensation.mapper.TaxDeductionMapper;
import org.springframework.stereotype.Service;

/**
 * 薪酬密域读取服务（OPEN-DECISIONS Phase 2 薪酬密域，CMP_SALARY_FORBIDDEN 下沉）。
 *
 * <p>双层语义：控制器已挂实体通用读权限（切面挡垂直越权返 20403）；本服务层校验密域权限
 * cmp:salary:read，缺失抛 CMP_SALARY_FORBIDDEN(54001) 而非通用 FORBIDDEN，
 * 让调用方明确区分「无功能权限」与「无密域权限」，同时防内部/Feign 路径绕过切面。</p>
 */
@Service
public class SalarySecretService {

    /** 密域读权限点。 */
    public static final String PERM_SALARY_READ = "cmp:salary:read";

    private final PayslipMapper payslipMapper;
    private final PayrollDetailMapper payrollDetailMapper;
    private final EmployeePayMapper employeePayMapper;
    private final TaxDeductionMapper taxDeductionMapper;

    public SalarySecretService(PayslipMapper payslipMapper, PayrollDetailMapper payrollDetailMapper,
                               EmployeePayMapper employeePayMapper, TaxDeductionMapper taxDeductionMapper) {
        this.payslipMapper = payslipMapper;
        this.payrollDetailMapper = payrollDetailMapper;
        this.employeePayMapper = employeePayMapper;
        this.taxDeductionMapper = taxDeductionMapper;
    }

    public PayslipSecretResponse payslip(Long id) {
        requireSalaryPerm();
        Payslip e = payslipMapper.selectById(id);
        if (e == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "工资单不存在");
        }
        return new PayslipSecretResponse(e.getId(), e.getEmpId(), e.getNetPayCipher(), e.getNetPayMask(),
                e.getUpdatedAt());
    }

    public PayrollDetailSecretResponse payrollDetail(Long id) {
        requireSalaryPerm();
        PayrollDetail e = payrollDetailMapper.selectById(id);
        if (e == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "核算明细不存在");
        }
        return new PayrollDetailSecretResponse(e.getId(), e.getEmpId(), e.getAmountCipher(), e.getAmountMask(),
                e.getNetAmountCipher(), e.getNetAmountMask(), e.getUpdatedAt());
    }

    public EmployeePaySecretResponse employeePay(Long id) {
        requireSalaryPerm();
        EmployeePay e = employeePayMapper.selectById(id);
        if (e == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "员工定薪不存在");
        }
        return new EmployeePaySecretResponse(e.getId(), e.getEmpId(), e.getSalaryAmountCipher(),
                e.getSalaryAmountMask(), e.getBankAccountCipher(), e.getBankAccountMask(), e.getUpdatedAt());
    }

    public TaxDeductionSecretResponse taxDeduction(Long id) {
        requireSalaryPerm();
        TaxDeduction e = taxDeductionMapper.selectById(id);
        if (e == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "专项附加扣除不存在");
        }
        return new TaxDeductionSecretResponse(e.getId(), e.getEmpId(), e.getDeductionAmountCipher(),
                e.getDeductionAmountMask(), e.getUpdatedAt());
    }

    private void requireSalaryPerm() {
        var perms = AuthContextHolder.getPerms();
        if (perms == null || !perms.contains(PERM_SALARY_READ)) {
            throw new BizException(ErrorCode.CMP_SALARY_FORBIDDEN);
        }
    }
}
