package com.hris.compensation.service;

import com.hris.compensation.dto.PayrollCompareDtos.EmployeeCompare;
import com.hris.compensation.dto.PayrollCompareDtos.PayrollCompareResponse;
import com.hris.compensation.entity.PayrollDetail;
import com.hris.compensation.entity.PayrollRun;
import com.hris.compensation.mapper.PayrollDetailMapper;
import com.hris.compensation.mapper.PayrollRunMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * 试算环比对比单测（CMP-F-013a）：逐项/应发/实发差异、基期缺省选择、无上期返回空。
 */
@ExtendWith(MockitoExtension.class)
class PayrollCompareServiceTest {

    private static final Long RUN_ID = 900001L;
    private static final Long BASE_RUN_ID = 900002L;

    @BeforeAll
    static void initLambdaCache() {
        // LambdaQueryWrapper 需要实体元数据缓存（否则 "can not find lambda cache for this entity"）
        com.baomidou.mybatisplus.core.MybatisConfiguration cfg = new com.baomidou.mybatisplus.core.MybatisConfiguration();
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(
                new org.apache.ibatis.builder.MapperBuilderAssistant(cfg, ""), PayrollRun.class);
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(
                new org.apache.ibatis.builder.MapperBuilderAssistant(cfg, ""), PayrollDetail.class);
    }

    @Mock
    private PayrollRunMapper runMapper;
    @Mock
    private PayrollDetailMapper detailMapper;

    private PayrollCompareService service;

    @BeforeEach
    void setUp() {
        service = new PayrollCompareService(runMapper, detailMapper);
    }

    private PayrollRun run(Long id, String period, int dayOfMonth) {
        PayrollRun e = new PayrollRun();
        e.setId(id);
        e.setPeriodCode(period);
        e.setStatus("CALC");
        e.setCreatedAt(LocalDateTime.of(2026, 2, dayOfMonth, 10, 0));
        return e;
    }

    private PayrollDetail detail(Long empId, String amount, String net, String snapshot) {
        PayrollDetail d = new PayrollDetail();
        d.setRunId(RUN_ID);
        d.setEmpId(empId);
        d.setAmountCipher(amount);
        d.setNetAmountCipher(net);
        d.setInputSnapshot(snapshot);
        return d;
    }

    private String snapshot(String itemsJson) {
        return "{\"periodCode\":\"2026-02\",\"base\":\"10000\",\"items\":" + itemsJson + "}";
    }

    @Test
    @DisplayName("缺省基期=同期间上一批次：逐项/应发/实发差异与环比正确")
    void comparesAgainstPreviousRun() {
        when(runMapper.selectById(RUN_ID)).thenReturn(run(RUN_ID, "2026-02", 10));
        when(runMapper.selectOne(any())).thenReturn(run(BASE_RUN_ID, "2026-02", 5));
        when(detailMapper.selectList(any())).thenReturn(
                List.of(detail(1L, "11000", "9500",
                        snapshot("{\"basic\":\"10000\",\"bonus\":\"800\",\"allowance\":\"200\"}"))),
                List.of(detail(1L, "10500", "9000",
                        snapshot("{\"basic\":\"10000\",\"bonus\":\"500\"}"))));

        PayrollCompareResponse resp = service.compare(RUN_ID, null);

        assertEquals(BASE_RUN_ID, resp.baseRunId());
        assertEquals("2026-02", resp.basePeriodCode());
        assertEquals(1, resp.employeeCount());
        assertEquals(0, new BigDecimal("500").compareTo(resp.grossDiff()));
        assertEquals(0, new BigDecimal("500").compareTo(resp.netDiff()));
        assertEquals(new BigDecimal("0.0556"), resp.changeRate());
        assertNull(resp.note());

        EmployeeCompare emp = resp.employees().get(0);
        assertEquals(1L, emp.empId());
        assertEquals(3, emp.items().size());
        assertEquals(0, new BigDecimal("300").compareTo(itemDiff(emp, "bonus")));
        assertEquals(0, new BigDecimal("200").compareTo(itemDiff(emp, "allowance")));
    }

    @Test
    @DisplayName("无上期批次 → 空列表 + 说明，不抛异常")
    void returnsEmptyWhenNoBaseRun() {
        when(runMapper.selectById(RUN_ID)).thenReturn(run(RUN_ID, "2026-02", 10));
        when(runMapper.selectOne(any())).thenReturn(null);

        PayrollCompareResponse resp = service.compare(RUN_ID, null);

        assertNull(resp.baseRunId());
        assertTrue(resp.employees().isEmpty());
        assertEquals(0, BigDecimal.ZERO.compareTo(resp.grossDiff()));
        assertTrue(resp.note() != null && !resp.note().isBlank());
    }

    @Test
    @DisplayName("员工并集：仅上期/仅本期员工均出现，缺失侧记 0")
    void unionsEmployeesFromBothRuns() {
        when(runMapper.selectById(RUN_ID)).thenReturn(run(RUN_ID, "2026-02", 10));
        when(runMapper.selectOne(any())).thenReturn(run(BASE_RUN_ID, "2026-02", 5));
        when(detailMapper.selectList(any())).thenReturn(
                List.of(detail(2L, "5000", "4500", snapshot("{\"basic\":\"5000\"}"))),
                List.of(detail(1L, "3000", "2800", snapshot("{\"basic\":\"3000\"}"))));

        PayrollCompareResponse resp = service.compare(RUN_ID, null);

        assertEquals(2, resp.employeeCount());
        EmployeeCompare left = resp.employees().get(0);
        EmployeeCompare newcomer = resp.employees().get(1);
        assertEquals(1L, left.empId());
        assertEquals(0, new BigDecimal("2800").negate().compareTo(left.netDiff()));
        assertEquals(2L, newcomer.empId());
        assertEquals(0, new BigDecimal("4500").compareTo(newcomer.netDiff()));
    }

    private BigDecimal itemDiff(EmployeeCompare emp, String code) {
        return emp.items().stream().filter(i -> code.equals(i.code()))
                .findFirst().orElseThrow().diff();
    }
}
