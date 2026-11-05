package com.hris.compensation.service;

import com.hris.common.api.R;
import com.hris.common.exception.BizException;
import com.hris.compensation.client.EmployeeClient;
import com.hris.compensation.client.OrgClient;
import com.hris.compensation.dto.ReportDtos.EmploymentBriefRow;
import com.hris.compensation.dto.ReportDtos.HeadcountStructureBriefRequest;
import com.hris.compensation.dto.ReportDtos.LegalEntityBriefItem;
import com.hris.compensation.dto.ReportDtos.LegalEntityBriefRequest;
import com.hris.compensation.dto.ReportDtos.LegalEntityCostRow;
import com.hris.compensation.entity.PayrollDetail;
import com.hris.compensation.entity.PayrollRun;
import com.hris.compensation.mapper.PayrollDetailMapper;
import com.hris.compensation.mapper.PayrollRunMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * CMP-F-017 多法人成本合并报表单测：聚合正确性、无明细空结果、org/emp 不可用降级。
 */
@ExtendWith(MockitoExtension.class)
class LegalEntityCostReportServiceTest {

    private static final String PERIOD = "2026-01";

    @Mock
    private PayrollRunMapper runMapper;
    @Mock
    private PayrollDetailMapper detailMapper;
    @Mock
    private EmployeeClient employeeClient;
    @Mock
    private OrgClient orgClient;

    private LegalEntityCostReportService service;

    @BeforeEach
    void setUp() {
        service = new LegalEntityCostReportService(runMapper, detailMapper, employeeClient, orgClient);
    }

    private PayrollRun run(long id) {
        PayrollRun r = new PayrollRun();
        r.setId(id);
        r.setPeriodCode(PERIOD);
        return r;
    }

    private PayrollDetail detail(long runId, long empId, String amount, String net) {
        PayrollDetail d = new PayrollDetail();
        d.setRunId(runId);
        d.setEmpId(empId);
        d.setAmountCipher(amount);
        d.setNetAmountCipher(net);
        return d;
    }

    @Nested
    @DisplayName("聚合正确性")
    class Aggregation {

        @Test
        @DisplayName("多批次多员工按法人聚合：金额 DECIMAL 求和、人数按员工去重")
        void aggregatesByLegalEntity() {
            when(runMapper.selectList(any())).thenReturn(List.of(run(1L), run(2L)));
            when(detailMapper.selectList(any())).thenReturn(List.of(
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
            when(runMapper.selectList(any())).thenReturn(List.of(run(1L)));
            when(detailMapper.selectList(any())).thenReturn(List.of(
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
    }

    @Nested
    @DisplayName("空结果")
    class EmptyResult {

        @Test
        @DisplayName("无算薪批次 → 空列表，不查明细不调 Feign")
        void emptyWhenNoRuns() {
            when(runMapper.selectList(any())).thenReturn(List.of());

            assertTrue(service.legalEntityCost(PERIOD).isEmpty());

            verify(detailMapper, never()).selectList(any());
            verify(employeeClient, never()).headcountStructureBrief(any());
            verify(orgClient, never()).legalEntityBrief(any());
        }

        @Test
        @DisplayName("有批次无明细 → 空列表")
        void emptyWhenNoDetails() {
            when(runMapper.selectList(any())).thenReturn(List.of(run(1L)));
            when(detailMapper.selectList(any())).thenReturn(List.of());

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
            when(runMapper.selectList(any())).thenReturn(List.of(run(1L)));
            when(detailMapper.selectList(any())).thenReturn(List.of(detail(1L, 100L, "1000", "800")));
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
            when(runMapper.selectList(any())).thenReturn(List.of(run(1L)));
            when(detailMapper.selectList(any())).thenReturn(List.of(
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
            when(runMapper.selectList(any())).thenReturn(List.of(run(1L)));
            when(detailMapper.selectList(any())).thenReturn(List.of(
                    detail(1L, 100L, "not-a-number", "800")));
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
