package com.hris.compensation.service;

import com.hris.common.trace.TraceContext;
import com.hris.compensation.dto.CostSnapshotDtos.CostSnapshotRow;
import com.hris.compensation.dto.CostSnapshotDtos.RefreshResponse;
import com.hris.compensation.dto.ReportDtos.LegalEntityCostRow;
import com.hris.compensation.entity.CostSnapshot;
import com.hris.compensation.mapper.CostSnapshotMapper;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 聚合库物化快照单测（批 12 #29）：刷新 upsert 逐法人落库 / 空期清理 /
 * 快照查询走 aggregateJdbcTemplate 且 0 号法人还原为 null。
 */
@ExtendWith(MockitoExtension.class)
class CostSnapshotServiceTest {

    private static final String PERIOD = "2026-01";
    private static final long TENANT = 99001L;

    @Mock
    private CostSnapshotMapper mapper;
    @Mock
    private JdbcTemplate aggregateJdbcTemplate;

    private CostSnapshotService service() {
        return new CostSnapshotService(mapper, aggregateJdbcTemplate);
    }

    @BeforeAll
    static void initTenant() {
        TraceContext.setTenantId(TENANT);
    }

    @AfterAll
    static void clearTenant() {
        TraceContext.setTenantId(null);
    }

    private static LegalEntityCostRow row(Long entityId, long headcount, String gross, String net) {
        BigDecimal g = new BigDecimal(gross);
        BigDecimal n = new BigDecimal(net);
        return new LegalEntityCostRow(entityId, "法人" + entityId, headcount, g, g.subtract(n), null, n);
    }

    @Test
    void refreshUpsertsEachLegalEntity() {
        when(mapper.upsert(any(CostSnapshot.class))).thenReturn(1);
        RefreshResponse resp = service().refresh(PERIOD, List.of(
                row(10L, 3, "30000.0000", "24000.0000"),
                row(null, 1, "10000.0000", "8000.0000")));

        assertEquals(PERIOD, resp.periodCode());
        assertEquals(2, resp.rows());
        assertEquals(2, resp.affected());

        ArgumentCaptor<CostSnapshot> captor = ArgumentCaptor.forClass(CostSnapshot.class);
        verify(mapper, times(2)).upsert(captor.capture());
        List<CostSnapshot> saved = captor.getAllValues();
        assertEquals(10L, saved.get(0).getLegalEntityId());
        assertEquals(3, saved.get(0).getHeadcount());
        assertEquals(new BigDecimal("6000.0000"), saved.get(0).getDeductionTotal());
        assertEquals(0L, saved.get(1).getLegalEntityId(), "法人未知行以 0 号哨兵落库，保证唯一键生效");
        assertEquals(TENANT, saved.get(0).getTenantId());
        verify(mapper).deleteStale(eq(TENANT), eq(PERIOD), any());
    }

    @Test
    void refreshSecondRunUpdatesSameRows() {
        when(mapper.upsert(any(CostSnapshot.class))).thenReturn(2);
        RefreshResponse first = service().refresh(PERIOD, List.of(row(10L, 3, "30000.0000", "24000.0000")));
        RefreshResponse second = service().refresh(PERIOD, List.of(row(10L, 4, "40000.0000", "32000.0000")));
        assertEquals(1, first.rows());
        assertEquals(1, second.rows());
        verify(mapper, times(2)).upsert(any(CostSnapshot.class));
    }

    @Test
    void refreshEmptyPeriodClearsSnapshotRows() {
        RefreshResponse resp = service().refresh(PERIOD, List.of());
        assertEquals(0, resp.rows());
        verify(mapper, times(1)).deleteStale(eq(TENANT), eq(PERIOD), isNull());
    }

    @Test
    void listReadsSnapshotFromAggregateDataSource() {
        when(aggregateJdbcTemplate.query(contains("cmp_cost_snapshot"), any(RowMapper.class),
                any(Object[].class))).thenAnswer(inv -> {
                    RowMapper<CostSnapshotRow> rm = inv.getArgument(1);
                    ResultSet rs = mock(ResultSet.class);
                    when(rs.getLong("legal_entity_id")).thenReturn(0L);
                    when(rs.getInt("headcount")).thenReturn(5);
                    when(rs.getBigDecimal("gross_total")).thenReturn(new BigDecimal("50000.0000"));
                    when(rs.getBigDecimal("deduction_total")).thenReturn(new BigDecimal("10000.0000"));
                    when(rs.getBigDecimal("tax_total")).thenReturn(null);
                    when(rs.getBigDecimal("net_total")).thenReturn(new BigDecimal("40000.0000"));
                    when(rs.getTimestamp("refreshed_at")).thenReturn(Timestamp.valueOf(
                            LocalDateTime.of(2026, 2, 1, 2, 0)));
                    return List.of(rm.mapRow(rs, 0));
                });

        List<CostSnapshotRow> rows = service().list(PERIOD);
        assertEquals(1, rows.size());
        assertNull(rows.get(0).legalEntityId(), "0 号哨兵出参还原为 null（法人未知）");
        assertEquals(5, rows.get(0).headcount());
        assertEquals(new BigDecimal("40000.0000"), rows.get(0).netTotal());
        assertNull(rows.get(0).taxTotal());
        assertEquals(LocalDateTime.of(2026, 2, 1, 2, 0), rows.get(0).refreshedAt());
        verify(aggregateJdbcTemplate).query(anyString(), any(RowMapper.class), any(Object[].class));
    }
}
