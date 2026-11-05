package com.hris.compensation.service;

import com.hris.common.api.R;
import com.hris.common.exception.BizException;
import com.hris.common.trace.TraceContext;
import com.hris.compensation.client.EmployeeClient;
import com.hris.compensation.client.OrgClient;
import com.hris.compensation.dto.ReportDtos.EmploymentBriefRow;
import com.hris.compensation.dto.ReportDtos.HeadcountStructureBriefRequest;
import com.hris.compensation.dto.ReportDtos.LegalEntityBriefItem;
import com.hris.compensation.dto.ReportDtos.LegalEntityBriefRequest;
import com.hris.compensation.dto.ReportDtos.LegalEntityCostRow;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * CMP-F-017 多法人成本合并报表单测：聚合正确性、无明细空结果、org/emp 不可用降级、
 * 聚合查询走 aggregateJdbcTemplate（显式租户/逻辑删除过滤，批 8 #17）。
 */
@ExtendWith(MockitoExtension.class)
class LegalEntityCostReportServiceTest {

    private static final String PERIOD = "2026-01";

    @Mock
    private JdbcTemplate jdbcTemplate;
    @Mock
    private EmployeeClient employeeClient;
    @Mock
    private OrgClient orgClient;

    private LegalEntityCostReportService service;

    @BeforeAll
    static void initTenant() {
        TraceContext.setTenantId(99001L);
    }

    @AfterAll
    static void clearTenant() {
        TraceContext.clear();
    }

    @BeforeEach
    void setUp() {
        service = new LegalEntityCostReportService(jdbcTemplate, employeeClient, orgClient);
    }

    /** 按 SQL 目标表分流 stub：run 查询返回 runIds，明细查询返回 rows。 */
    private void stubQueries(List<Long> runIds, List<LegalEntityCostReportService.DetailRow> rows) {
        when(jdbcTemplate.query(contains("cmp_payroll_run"), any(RowMapper.class),
                any(Object[].class))).thenReturn(runIds);
        when(jdbcTemplate.query(contains("cmp_payroll_detail"), any(RowMapper.class),
                any(Object[].class))).thenReturn(rows);
    }

    private LegalEntityCostReportService.DetailRow detail(long runId, long empId,
                                                          String amount, String net) {
        return new LegalEntityCostReportService.DetailRow(runId, empId, amount, net);
    }

    @Nested
    @DisplayName("聚合正确性")
    class Aggregation {

        @Test
        @DisplayName("多批次多员工按法人聚合：金额 DECIMAL 求和、人数按员工去重")
        void aggregatesByLegalEntity() {
            stubQueries(List.of(1L, 2L), List.of(
                    detail(1L, 100L, "10000.50", "8000.25"),
                    detail(2L, 100L, "500.00", "400.00"),
                    detail(2L, 200L, "12000.00", "9600.00")));
            when(employeeClient.headcountStructureBrief(any(HeadcountStructureBriefRequest.class)))
                    .thenReturn(R.ok(List.of(
                            new EmploymentBriefRow(100L, 10L, 5L, "FULLTIME", 2),
                            new EmploymentBriefRow(200L, 20L, 6L, "FULLTIME", 2))));
            when(orgClient.legalEntityBrief(any(LegalEntityBriefRequest.class)))
                    .thenReturn(R.ok(List.of(
                            new LegalEntityBriefItem(10L, "法人甲"),
                            new LegalEntityBriefItem(20L, "法人乙"))));

            List<LegalEntityCostRow> rows = service.legalEntityCost(PERIOD);

            assertEquals(2, rows.size());
            LegalEntityCostRow e10 = rows.get(0);
            assertEquals(10L, e10.legalEntityId());
            assertEquals("法人甲", e10.legalEntityName());
            assertEquals(1, e10.headcount());
            assertEquals(0, new BigDecimal("10500.50").compareTo(e10.grossTotal()));
            assertEquals(0, new BigDecimal("8400.25").compareTo(e10.netTotal()));
            assertEquals(0, new BigDecimal("2100.25").compareTo(e10.deductionTotal()));
            assertNull(e10.taxTotal());
            LegalEntityCostRow e20 = rows.get(1);
            assertEquals(20L, e20.legalEntityId());
            assertEquals("法人乙", e20.legalEntityName());
            assertEquals(0, new BigDecimal("12000").compareTo(e20.grossTotal()));
        }

        @Test
        @DisplayName("同一法人多员工：headcount 按员工去重而非明细行数")
        void headcountDeduplicatesEmp() {
            stubQueries(List.of(1L), List.of(
                    detail(1L, 100L, "1000", "800"),
                    detail(1L, 101L, "2000", "1600"),
                    detail(1L, 102L, "3000", "2400")));
            when(employeeClient.headcountStructureBrief(any(HeadcountStructureBriefRequest.class)))
                    .thenReturn(R.ok(List.of(
                            new EmploymentBriefRow(100L, 10L, 5L, "FULLTIME", 2),
                            new EmploymentBriefRow(101L, 10L, 5L, "FULLTIME", 2),
                            new EmploymentBriefRow(102L, 10L, 5L, "FULLTIME", 2))));
            when(orgClient.legalEntityBrief(any(LegalEntityBriefRequest.class)))
                    .thenReturn(R.ok(List.of(new LegalEntityBriefItem(10L, "法人甲"))));

            List<LegalEntityCostRow> rows = service.legalEntityCost(PERIOD);

            assertEquals(1, rows.size());
            assertEquals(3, rows.get(0).headcount());
            assertEquals(0, new BigDecimal("6000").compareTo(rows.get(0).grossTotal()));
        }

        @Test
        @DisplayName("聚合查询 SQL 显式携带租户与逻辑删除过滤（脱离 MP 插件链路后隔离不变）")
        void sqlCarriesTenantAndDeleteFilter() {
            stubQueries(List.of(1L), List.of(
                    detail(1L, 100L, "1000", "800")));
            when(employeeClient.headcountStructureBrief(any(HeadcountStructureBriefRequest.class)))
                    .thenReturn(R.ok(List.of(new EmploymentBriefRow(100L, 10L, 5L, "FULLTIME", 2))));
            when(orgClient.legalEntityBrief(any(LegalEntityBriefRequest.class)))
                    .thenReturn(R.ok(List.of(new LegalEntityBriefItem(10L, "法人甲"))));

            service.legalEntityCost(PERIOD);

            // run 与明细两条聚合查询均显式携带租户/逻辑删除过滤
            verify(jdbcTemplate, org.mockito.Mockito.times(2)).query(
                    contains("tenant_id = ? AND deleted = 0"), any(RowMapper.class), any(Object[].class));
        }
    }

    @Nested
    @DisplayName("空结果")
    class EmptyResult {

        @Test
        @DisplayName("无算薪批次 → 空列表，不查明细不调 Feign")
        void emptyWhenNoRuns() {
            when(jdbcTemplate.query(contains("cmp_payroll_run"), any(RowMapper.class),
                    any(Object[].class))).thenReturn(List.of());

            assertTrue(service.legalEntityCost(PERIOD).isEmpty());

            verify(jdbcTemplate, never()).query(contains("cmp_payroll_detail"),
                    any(RowMapper.class), any(Object[].class));
            verify(employeeClient, never()).headcountStructureBrief(any());
            verify(orgClient, never()).legalEntityBrief(any());
        }

        @Test
        @DisplayName("有批次无明细 → 空列表")
        void emptyWhenNoDetails() {
            stubQueries(List.of(1L), List.of());

            assertTrue(service.legalEntityCost(PERIOD).isEmpty());

            verify(employeeClient, never()).headcountStructureBrief(any());
        }

        @Test
        @DisplayName("periodCode 为空 → PARAM_INVALID")
        void rejectsBlankPeriod() {
            assertThrows(BizException.class, () -> service.legalEntityCost(" "));
            assertThrows(BizException.class, () -> service.legalEntityCost(null));
        }
    }

    @Nested
    @DisplayName("跨域降级")
    class Degradation {

        @Test
        @DisplayName("org 不可用 → 法人名称降级为 null，聚合行照常返回（fail-open）")
        void degradesWhenOrgUnavailable() {
            stubQueries(List.of(1L), List.of(detail(1L, 100L, "1000", "800")));
            when(employeeClient.headcountStructureBrief(any(HeadcountStructureBriefRequest.class)))
                    .thenReturn(R.ok(List.of(new EmploymentBriefRow(100L, 10L, 5L, "FULLTIME", 2))));
            when(orgClient.legalEntityBrief(any(LegalEntityBriefRequest.class)))
                    .thenThrow(new RuntimeException("org down"));

            List<LegalEntityCostRow> rows = service.legalEntityCost(PERIOD);

            assertEquals(1, rows.size());
            assertEquals(10L, rows.get(0).legalEntityId());
            assertNull(rows.get(0).legalEntityName());
            assertEquals(0, new BigDecimal("1000").compareTo(rows.get(0).grossTotal()));
        }

        @Test
        @DisplayName("emp 简报不可用 → 法人归属降级为未知（legalEntityId=null 单行）")
        void degradesWhenEmpUnavailable() {
            stubQueries(List.of(1L), List.of(
                    detail(1L, 100L, "1000", "800"),
                    detail(1L, 101L, "2000", "1600")));
            when(employeeClient.headcountStructureBrief(any(HeadcountStructureBriefRequest.class)))
                    .thenThrow(new RuntimeException("emp down"));

            List<LegalEntityCostRow> rows = service.legalEntityCost(PERIOD);

            assertEquals(1, rows.size());
            assertNull(rows.get(0).legalEntityId());
            assertNull(rows.get(0).legalEntityName());
            assertEquals(2, rows.get(0).headcount());
            assertEquals(0, new BigDecimal("3000").compareTo(rows.get(0).grossTotal()));
        }

        @Test
        @DisplayName("金额列非数值 → 跳过该值不阻断聚合")
        void skipsNonNumericAmount() {
            stubQueries(List.of(1L), List.of(detail(1L, 100L, "not-a-number", "800")));
            when(employeeClient.headcountStructureBrief(any(HeadcountStructureBriefRequest.class)))
                    .thenReturn(R.ok(List.of(new EmploymentBriefRow(100L, 10L, 5L, "FULLTIME", 2))));
            when(orgClient.legalEntityBrief(any(LegalEntityBriefRequest.class)))
                    .thenReturn(R.ok(List.of(new LegalEntityBriefItem(10L, "法人甲"))));

            List<LegalEntityCostRow> rows = service.legalEntityCost(PERIOD);

            assertEquals(1, rows.size());
            assertEquals(0, BigDecimal.ZERO.compareTo(rows.get(0).grossTotal()));
            assertEquals(0, new BigDecimal("800").compareTo(rows.get(0).netTotal()));
        }
    }
}
