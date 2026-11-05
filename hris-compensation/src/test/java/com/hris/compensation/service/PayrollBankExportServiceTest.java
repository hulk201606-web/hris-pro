package com.hris.compensation.service;

import com.hris.common.api.R;
import com.hris.common.exception.BizException;
import com.hris.common.exception.ErrorCode;
import com.hris.compensation.client.EmployeeClient;
import com.hris.compensation.entity.EmployeePay;
import com.hris.compensation.entity.PayrollDetail;
import com.hris.compensation.entity.PayrollRun;
import com.hris.compensation.mapper.EmployeePayMapper;
import com.hris.compensation.mapper.PayrollDetailMapper;
import com.hris.compensation.mapper.PayrollRunMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * 报盘导出单测：状态守卫（仅 APPROVE/PAID/LOCK）、空明细拒绝、CSV 列与 BOM。
 */
@ExtendWith(MockitoExtension.class)
class PayrollBankExportServiceTest {

    private static final Long RUN_ID = 920001L;

    @BeforeAll
    static void initLambdaCache() {
        com.baomidou.mybatisplus.core.MybatisConfiguration cfg = new com.baomidou.mybatisplus.core.MybatisConfiguration();
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(
                new org.apache.ibatis.builder.MapperBuilderAssistant(cfg, ""), PayrollDetail.class);
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(
                new org.apache.ibatis.builder.MapperBuilderAssistant(cfg, ""), EmployeePay.class);
    }

    @Mock
    private PayrollRunMapper runMapper;
    @Mock
    private PayrollDetailMapper detailMapper;
    @Mock
    private EmployeePayMapper employeePayMapper;
    @Mock
    private EmployeeClient employeeClient;

    private PayrollBankExportService service;

    @BeforeEach
    void setUp() {
        service = new PayrollBankExportService(runMapper, detailMapper, employeePayMapper, employeeClient);
    }

    private PayrollRun run(String status) {
        PayrollRun e = new PayrollRun();
        e.setId(RUN_ID);
        e.setPeriodCode("2026-02");
        e.setPayGroupId(1L);
        e.setStatus(status);
        return e;
    }

    @Test
    @DisplayName("INIT 状态 → PARAM_INVALID（未过复核不可导出）")
    void rejectsNonExportableStatus() {
        when(runMapper.selectById(RUN_ID)).thenReturn(run("INIT"));

        BizException e = assertThrows(BizException.class, () -> service.export(RUN_ID));

        assertEquals(ErrorCode.PARAM_INVALID.getCode(), e.getCode());
    }

    @Test
    @DisplayName("空明细 → PARAM_INVALID，不产出只有表头的空报盘")
    void rejectsEmptyDetails() {
        when(runMapper.selectById(RUN_ID)).thenReturn(run("APPROVE"));
        when(detailMapper.selectList(any())).thenReturn(List.of());

        BizException e = assertThrows(BizException.class, () -> service.export(RUN_ID));

        assertEquals(ErrorCode.PARAM_INVALID.getCode(), e.getCode());
    }

    @Test
    @DisplayName("APPROVE 状态 → CSV 含 BOM、5 列契约、解密卡号与实发金额、通用模板备注")
    void exportsCsvWithContractColumns() {
        when(runMapper.selectById(RUN_ID)).thenReturn(run("APPROVE"));
        PayrollDetail d = new PayrollDetail();
        d.setRunId(RUN_ID);
        d.setEmpId(1L);
        d.setNetAmountCipher("9500.00");
        when(detailMapper.selectList(any())).thenReturn(List.of(d));

        EmployeePay pay = new EmployeePay();
        pay.setEmpId(1L);
        pay.setPayGroupId(1L);
        pay.setBankAccountCipher("6222001123456");
        when(employeePayMapper.selectList(any())).thenReturn(List.of(pay));
        when(employeeClient.profileBrief(1L)).thenReturn(R.ok(Map.of("empNo", "E001", "name", "张三")));

        PayrollBankExportService.BankExportFile file = service.export(RUN_ID);

        assertEquals("payroll_2026-02.csv", file.filename());
        String[] lines = file.content().split("\r\n");
        assertEquals("\uFEFF员工工号,姓名,银行卡号,实发金额,备注", lines[0]);
        assertEquals("E001,张三,6222001123456,9500.00," + PayrollBankExportService.BANK_TEMPLATE_NOTE,
                lines[1]);
        assertEquals(5, lines[1].split(",").length);
        assertTrue(PayrollBankExportService.BANK_TEMPLATE_NOTE.contains("各行差异需按银行适配"));
    }

    @Test
    @DisplayName("员工域不可用 → 工号降级为 empId 占位，导出不失败")
    void degradesWhenEmployeeServiceUnavailable() {
        when(runMapper.selectById(RUN_ID)).thenReturn(run("PAID"));
        PayrollDetail d = new PayrollDetail();
        d.setRunId(RUN_ID);
        d.setEmpId(7L);
        d.setNetAmountCipher("100");
        when(detailMapper.selectList(any())).thenReturn(List.of(d));
        when(employeePayMapper.selectList(any())).thenReturn(List.of());
        when(employeeClient.profileBrief(7L)).thenThrow(new RuntimeException("emp down"));

        PayrollBankExportService.BankExportFile file = service.export(RUN_ID);

        String[] lines = file.content().split("\r\n");
        assertTrue(lines[1].startsWith("7,,"));
    }
}
