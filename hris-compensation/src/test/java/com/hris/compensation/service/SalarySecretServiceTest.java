package com.hris.compensation.service;

import com.hris.common.exception.BizException;
import com.hris.common.exception.ErrorCode;
import com.hris.common.security.AuthContextHolder;
import com.hris.compensation.dto.SalarySecretDtos.PayslipSecretResponse;
import com.hris.compensation.entity.PayrollDetail;
import com.hris.compensation.entity.Payslip;
import com.hris.compensation.mapper.EmployeePayMapper;
import com.hris.compensation.mapper.PayrollDetailMapper;
import com.hris.compensation.mapper.PayslipMapper;
import com.hris.compensation.mapper.TaxDeductionMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

/**
 * SalarySecretService 单元测试（薪酬密域双层语义，OPEN-DECISIONS Phase 2）。
 *
 * <p>锁定行为：持 cmp:salary:read → 返回密文；缺失 → CMP_SALARY_FORBIDDEN(54001)；
 * 单据不存在 → NOT_FOUND(10002)。密域权限经 AuthContextHolder.getPerms() 校验
 * （ThreadLocal，测试前后须清理，防串用例）。</p>
 */
@ExtendWith(MockitoExtension.class)
class SalarySecretServiceTest {

    @Mock
    private PayslipMapper payslipMapper;
    @Mock
    private PayrollDetailMapper payrollDetailMapper;
    @Mock
    private EmployeePayMapper employeePayMapper;
    @Mock
    private TaxDeductionMapper taxDeductionMapper;

    private SalarySecretService service;

    @BeforeEach
    void setUp() {
        service = new SalarySecretService(payslipMapper, payrollDetailMapper,
                employeePayMapper, taxDeductionMapper);
    }

    @AfterEach
    void tearDown() {
        AuthContextHolder.clear();
    }

    private Payslip stubPayslip() {
        Payslip p = new Payslip();
        p.setId(900101L);
        p.setEmpId(200001L);
        p.setNetPayCipher("CIPHER-XYZ");
        p.setNetPayMask("18,***.**");
        return p;
    }

    @Nested
    @DisplayName("密域权限校验（双层语义）")
    class PermGate {

        @Test
        @DisplayName("持 cmp:salary:read → 返回密文与掩码")
        void returnsCipherWhenPermitted() {
            AuthContextHolder.setPerms(Set.of(SalarySecretService.PERM_SALARY_READ));
            when(payslipMapper.selectById(900101L)).thenReturn(stubPayslip());

            PayslipSecretResponse resp = service.payslip(900101L);

            assertEquals("CIPHER-XYZ", resp.netPayCipher());
            assertEquals("18,***.**", resp.netPayMask());
        }

        @Test
        @DisplayName("缺失密域权限（仅有单据读权限）→ CMP_SALARY_FORBIDDEN(54001)")
        void throwsForbiddenWithoutSalaryPerm() {
            AuthContextHolder.setPerms(Set.of("cmp:payslip:read"));

            BizException e = assertThrows(BizException.class, () -> service.payslip(900101L));
            assertEquals(ErrorCode.CMP_SALARY_FORBIDDEN.getCode(), e.getCode());
        }

        @Test
        @DisplayName("perms 为空 → 同样抛 54001（不泄露单据存在性）")
        void throwsForbiddenWhenPermsNull() {
            BizException e = assertThrows(BizException.class, () -> service.payslip(900101L));
            assertEquals(ErrorCode.CMP_SALARY_FORBIDDEN.getCode(), e.getCode());
        }
    }

    @Nested
    @DisplayName("单据不存在")
    class NotFound {

        @Test
        @DisplayName("工资单不存在 → NOT_FOUND(10002)")
        void payslipNotFound() {
            AuthContextHolder.setPerms(Set.of(SalarySecretService.PERM_SALARY_READ));
            when(payslipMapper.selectById(999L)).thenReturn(null);

            BizException e = assertThrows(BizException.class, () -> service.payslip(999L));
            assertEquals(ErrorCode.NOT_FOUND.getCode(), e.getCode());
        }

        @Test
        @DisplayName("核算明细不存在 → NOT_FOUND(10002)")
        void payrollDetailNotFound() {
            AuthContextHolder.setPerms(Set.of(SalarySecretService.PERM_SALARY_READ));
            when(payrollDetailMapper.selectById(999L)).thenReturn(null);

            BizException e = assertThrows(BizException.class, () -> service.payrollDetail(999L));
            assertEquals(ErrorCode.NOT_FOUND.getCode(), e.getCode());
        }
    }

    @Nested
    @DisplayName("其余三类密域读取")
    class OtherEntities {

        @Test
        @DisplayName("核算明细：双密文字段返回")
        void payrollDetailReturnsBothCiphers() {
            AuthContextHolder.setPerms(Set.of(SalarySecretService.PERM_SALARY_READ));
            PayrollDetail d = new PayrollDetail();
            d.setId(900201L);
            d.setEmpId(200001L);
            d.setAmountCipher("C-AMT");
            d.setNetAmountCipher("C-NET");
            when(payrollDetailMapper.selectById(900201L)).thenReturn(d);

            var resp = service.payrollDetail(900201L);

            assertEquals("C-AMT", resp.amountCipher());
            assertEquals("C-NET", resp.netAmountCipher());
        }
    }
}
