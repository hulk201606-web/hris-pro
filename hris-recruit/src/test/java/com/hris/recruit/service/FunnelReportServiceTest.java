package com.hris.recruit.service;

import com.hris.recruit.dto.PublicRecruitDtos.FunnelItem;
import com.hris.recruit.mapper.StageLogMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * FunnelReportService 单元测试（RCS-F-020）：聚合映射与转化率计算正确性。
 */
@ExtendWith(MockitoExtension.class)
class FunnelReportServiceTest {

    @Mock
    private StageLogMapper stageLogMapper;

    private FunnelReportService service;

    @BeforeEach
    void setUp() {
        service = new FunnelReportService(stageLogMapper);
    }

    private Map<String, Object> row(String stage, long cnt) {
        return Map.of("stage", stage, "cnt", cnt);
    }

    @Test
    @DisplayName("聚合正确：count 透传 + conversion=count/APPLIED（HALF_UP 2 位小数），阶段按常量顺序输出")
    void aggregatesCountsAndConversion() {
        when(stageLogMapper.selectFunnelCounts(any(), any(), any())).thenReturn(List.of(
                row("APPLIED", 200), row("SCREEN", 100), row("INTERVIEW", 51),
                row("OFFER", 25), row("HIRED", 10), row("REJECTED", 99)));

        List<FunnelItem> funnel = service.funnel(null, null, null);

        assertEquals(5, funnel.size(), "仅输出漏斗常量阶段，拒绝态不入漏斗");
        assertEquals("APPLIED", funnel.get(0).stage());
        assertEquals(200, funnel.get(0).count());
        assertEquals(0, BigDecimal.valueOf(100.00).compareTo(funnel.get(0).conversion()));
        assertEquals(0, BigDecimal.valueOf(50.00).compareTo(funnel.get(1).conversion()));
        assertEquals(0, BigDecimal.valueOf(25.50).compareTo(funnel.get(2).conversion()));
        assertEquals(0, BigDecimal.valueOf(12.50).compareTo(funnel.get(3).conversion()));
        assertEquals(0, BigDecimal.valueOf(5.00).compareTo(funnel.get(4).conversion()));
    }

    @Test
    @DisplayName("APPLIED 无记录时全阶段 count=0、conversion=0，不出现除零")
    void returnsZerosWhenNoApplied() {
        when(stageLogMapper.selectFunnelCounts(any(), any(), any()))
                .thenReturn(List.of(row("SCREEN", 3)));

        List<FunnelItem> funnel = service.funnel(null, null, null);

        assertEquals(0, funnel.get(0).count());
        assertEquals(0, BigDecimal.ZERO.setScale(2).compareTo(funnel.get(0).conversion()));
        assertEquals(0, funnel.get(4).count());
    }

    @Test
    @DisplayName("from/to/reqId 透传 mapper：reqId 非 null 时按需求过滤")
    void passesFilterParamsToMapper() {
        LocalDateTime from = LocalDateTime.of(2026, 1, 1, 0, 0);
        LocalDateTime to = LocalDateTime.of(2026, 2, 1, 0, 0);
        when(stageLogMapper.selectFunnelCounts(any(), any(), any())).thenReturn(List.of());

        service.funnel(from, to, 700001L);

        verify(stageLogMapper).selectFunnelCounts(eq(from), eq(to), eq(700001L));
    }

    @Test
    @DisplayName("from/to 均不传时以 null 下推 mapper（全时间范围）")
    void passesNullsWhenNoRange() {
        when(stageLogMapper.selectFunnelCounts(any(), any(), any())).thenReturn(List.of());

        service.funnel(null, null, null);

        verify(stageLogMapper).selectFunnelCounts(isNull(), isNull(), isNull());
    }
}
