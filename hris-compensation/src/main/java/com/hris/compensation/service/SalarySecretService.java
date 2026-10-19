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

    /**
     * 密域读取：按 ID 查询工资单。
     * <p>先校验密域权限 cmp:salary:read，缺失抛 CMP_SALARY_FORBIDDEN(54001)。
     * 响应同时携带 netPayCipher（实发明文）与 netPayMask（掩码），非授权视图仅渲染掩码。</p>
     */
    public PayslipSecretResponse payslip(Long id) {
        requireSalaryPerm();
        Payslip e = payslipMapper.selectById(id);
        if (e == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "工资单不存在");
        }
        return new PayslipSecretResponse(e.getId(), e.getEmpId(), e.getNetPayCipher(), e.getNetPayMask(),
                e.getUpdatedAt());
    }

    /**
     * 密域读取：按 ID 查询算薪明细。
     * <p>校验 cmp:salary:read 后返回 amountCipher/netAmountCipher（应发/实发明文）与对应掩码列。</p>
     */
    public PayrollDetailSecretResponse payrollDetail(Long id) {
        requireSalaryPerm();
        PayrollDetail e = payrollDetailMapper.selectById(id);
        if (e == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "核算明细不存在");
        }
        return new PayrollDetailSecretResponse(e.getId(), e.getEmpId(), e.getAmountCipher(), e.getAmountMask(),
                e.getNetAmountCipher(), e.getNetAmountMask(), e.getUpdatedAt());
    }

    /**
     * 密域读取：按 ID 查询员工薪资档案。
     * <p>校验 cmp:salary:read 后返回 salaryAmountCipher（定薪明文）/bankAccountCipher（银行账号明文）与对应掩码列。</p>
     */
    public EmployeePaySecretResponse employeePay(Long id) {
        requireSalaryPerm();
        EmployeePay e = employeePayMapper.selectById(id);
        if (e == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "员工定薪不存在");
        }
        return new EmployeePaySecretResponse(e.getId(), e.getEmpId(), e.getSalaryAmountCipher(),
                e.getSalaryAmountMask(), e.getBankAccountCipher(), e.getBankAccountMask(), e.getUpdatedAt());
    }

    /**
     * 密域读取：按 ID 查询个税专项附加扣除。
     * <p>校验 cmp:salary:read 后返回 deductionAmountCipher（扣除金额明文）与掩码列。</p>
     */
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
