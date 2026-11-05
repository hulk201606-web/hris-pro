package com.hris.compensation.service;

import com.hris.common.exception.BizException;
import com.hris.common.exception.ErrorCode;
import com.hris.common.security.AuthContextHolder;
import com.hris.compensation.dto.EmployeePayHistoryDtos.EmployeePayHistoryItem;
import com.hris.compensation.entity.EmployeePay;
import com.hris.compensation.entity.PayrollAdjustment;
import com.hris.compensation.entity.PayrollRun;
import com.hris.compensation.mapper.EmployeePayMapper;
import com.hris.compensation.mapper.PayrollAdjustmentMapper;
import com.hris.compensation.mapper.PayrollRunMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * 调薪轨迹单测（CMP-F-018）：倒序排序、字段映射、缺失月份调整单补充、密域权限。
 */
@ExtendWith(MockitoExtension.class)
class EmployeePayHistoryServiceTest {

    private static final Long EMP_ID = 930001L;

    @BeforeAll
    static void initLambdaCache() {
        com.baomidou.mybatisplus.core.MybatisConfiguration cfg = new com.baomidou.mybatisplus.core.MybatisConfiguration();
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(
                new org.apache.ibatis.builder.MapperBuilderAssistant(cfg, ""), EmployeePay.class);
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(
                new org.apache.ibatis.builder.MapperBuilderAssistant(cfg, ""), PayrollAdjustment.class);
    }

    @Mock
    private EmployeePayMapper employeePayMapper;
    @Mock
    private PayrollAdjustmentMapper adjustmentMapper;
    @Mock
    private PayrollRunMapper runMapper;

    private EmployeePayHistoryService service;

    @BeforeEach
    void setUp() {
        service = new EmployeePayHistoryService(employeePayMapper, adjustmentMapper, runMapper);
        AuthContextHolder.setPerms(Set.of(EmployeePayHistoryService.PERM_SALARY_READ));
    }

    @AfterEach
    void tearDown() {
        AuthContextHolder.clear();
    }

    private EmployeePay version(String effectiveFrom, String amount, long createdBy) {
        EmployeePay e = new EmployeePay();
        e.setEmpId(EMP_ID);
        e.setEffectiveFrom(LocalDate.parse(effectiveFrom));
        e.setSalaryAmountCipher(amount);
        e.setCreatedBy(createdBy);
        e.setCreatedAt(LocalDateTime.of(2026, 1, 1, 9, 0));
        return e;
    }

    @Test
    @DisplayName("版本链按生效日倒序返回，字段映射正确（approvalId 无载体为 null）")
    void listsVersionsDescending() {
        when(employeePayMapper.selectList(any())).thenReturn(List.of(
                version("2026-01-01", "20000", 5L),
                version("2025-07-01", "18000", 6L)));
        when(adjustmentMapper.selectList(any())).thenReturn(List.of());

        List<EmployeePayHistoryItem> items = service.history(EMP_ID);

        assertEquals(2, items.size());
        EmployeePayHistoryItem latest = items.get(0);
        assertEquals(LocalDate.of(2026, 1, 1), latest.effectiveDate());
        assertEquals(0, new BigDecimal("20000").compareTo(latest.baseAmount()));
        assertEquals(5L, latest.createdBy());
        assertEquals(EmployeePayHistoryService.SOURCE_PAY_VERSION, latest.source());
        assertNull(latest.approvalId());
        assertEquals(LocalDate.of(2025, 7, 1), items.get(1).effectiveDate());
    }

    @Test
    @DisplayName("版本链缺失月份由调整单补充（生效日=批次期间月初，reason 带调整类型）")
    void supplementsWithAdjustment() {
        when(employeePayMapper.selectList(any())).thenReturn(List.of(version("2025-07-01", "18000", 6L)));
        PayrollAdjustment adj = new PayrollAdjustment();
        adj.setEmpId(EMP_ID);
        adj.setRunId(9L);
        adj.setAdjustType("BONUS");
        adj.setAmount(new BigDecimal("3000"));
        adj.setReason("项目奖");
        adj.setCreatedAt(LocalDateTime.of(2026, 3, 20, 10, 0));
        when(adjustmentMapper.selectList(any())).thenReturn(List.of(adj));
        PayrollRun run = new PayrollRun();
        run.setId(9L);
        run.setPeriodCode("2026-03");
        when(runMapper.selectBatchIds(any())).thenReturn(List.of(run));

        List<EmployeePayHistoryItem> items = service.history(EMP_ID);

        assertEquals(2, items.size());
        EmployeePayHistoryItem adjustment = items.get(0);
        assertEquals(LocalDate.of(2026, 3, 1), adjustment.effectiveDate());
        assertEquals(0, new BigDecimal("3000").compareTo(adjustment.baseAmount()));
        assertEquals("BONUS: 项目奖", adjustment.reason());
        assertEquals(EmployeePayHistoryService.SOURCE_ADJUSTMENT, adjustment.source());
    }

    @Test
    @DisplayName("无 cmp:salary:read → CMP_SALARY_FORBIDDEN，不查库")
    void requiresSalaryPerm() {
        AuthContextHolder.setPerms(Set.of("cmp:employee-pay:read"));

        BizException e = assertThrows(BizException.class, () -> service.history(EMP_ID));

        assertEquals(ErrorCode.CMP_SALARY_FORBIDDEN.getCode(), e.getCode());
    }
}
