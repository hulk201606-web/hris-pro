package com.hris.org.service;

import com.hris.org.dto.OccupancyRequest;
import com.hris.org.entity.OrgHeadcount;
import com.hris.org.entity.OrgHcOccupancy;
import com.hris.org.mapper.OrgHeadcountMapper;
import com.hris.org.mapper.OrgHcOccupancyMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * HeadcountService 的编制占用回写单测（§10.5-B8）。
 *
 * <p>从 HeadcountServiceTest 拆分而来（单文件 ≤300 行门禁），夹具与原类一致。
 * 锁定：delta 仅允许 +1/-1，同 transNo 幂等跳过，occupied 不得为负，缺编制行不静默新建。</p>
 */
@ExtendWith(MockitoExtension.class)
class HeadcountOccupancyTest {

    @Mock
    private OrgHeadcountMapper mapper;
    @Mock
    private OrgHcOccupancyMapper occupancyMapper;

    private HeadcountService service;

    @BeforeEach
    void setUp() {
        service = new HeadcountService(mapper, occupancyMapper);
    }

    private OrgHeadcount hc(Integer quota, Integer occupied) {
        OrgHeadcount h = new OrgHeadcount();
        h.setDeptId(1L);
        h.setPositionId(2L);
        h.setPeriod("2026Q1");
        h.setQuota(quota);
        h.setOccupied(occupied);
        return h;
    }

    @Nested
    @DisplayName("occupancy：编制占用回写（§10.5-B8）")
    class Occupancy {

        private OccupancyRequest req(int delta, String transNo) {
            return new OccupancyRequest(100L, 1L, 2L, "2026Q1", delta, transNo);
        }

        @Test
        @DisplayName("delta=+1 → occupied 增 1，落 OCCUPY 占用流水")
        void occupiesOnDeltaPlusOne() {
            OrgHeadcount existing = hc(5, 3);
            when(occupancyMapper.selectCount(any())).thenReturn(0L);
            when(mapper.selectOne(any())).thenReturn(existing);

            service.occupancy(req(1, "TXN001"));

            assertEquals(4, existing.getOccupied());
            verify(mapper).updateById(existing);
            ArgumentCaptor<OrgHcOccupancy> captor = ArgumentCaptor.forClass(OrgHcOccupancy.class);
            verify(occupancyMapper).insert(captor.capture());
            assertEquals("OCCUPY", captor.getValue().getChangeType());
            assertEquals("TXN001", captor.getValue().getTransNo());
            assertEquals(100L, captor.getValue().getEmpId());
        }

        @Test
        @DisplayName("delta=-1 → occupied 减 1，落 RELEASE 流水")
        void releasesOnDeltaMinusOne() {
            OrgHeadcount existing = hc(5, 3);
            when(occupancyMapper.selectCount(any())).thenReturn(0L);
            when(mapper.selectOne(any())).thenReturn(existing);

            service.occupancy(req(-1, "TXN002"));

            assertEquals(2, existing.getOccupied());
            ArgumentCaptor<OrgHcOccupancy> captor = ArgumentCaptor.forClass(OrgHcOccupancy.class);
            verify(occupancyMapper).insert(captor.capture());
            assertEquals("RELEASE", captor.getValue().getChangeType());
        }

        @Test
        @DisplayName("同 transNo 重复回写 → 幂等跳过，不更新不落流水")
        void idempotentByTransNo() {
            when(occupancyMapper.selectCount(any())).thenReturn(1L);

            service.occupancy(req(1, "TXN001"));

            verify(mapper, never()).selectOne(any());
            verify(mapper, never()).updateById(any(OrgHeadcount.class));
            verify(occupancyMapper, never()).insert(any(OrgHcOccupancy.class));
        }

        @Test
        @DisplayName("occupied-1 < 0 → PARAM_INVALID 守卫，不落流水")
        void guardsNegativeOccupied() {
            OrgHeadcount existing = hc(5, 0);
            when(occupancyMapper.selectCount(any())).thenReturn(0L);
            when(mapper.selectOne(any())).thenReturn(existing);

            com.hris.common.exception.BizException e = assertThrows(
                    com.hris.common.exception.BizException.class,
                    () -> service.occupancy(req(-1, "TXN003")));

            assertEquals(com.hris.common.exception.ErrorCode.PARAM_INVALID.getCode(), e.getCode());
            verify(occupancyMapper, never()).insert(any(OrgHcOccupancy.class));
        }

        @Test
        @DisplayName("编制计划缺行 → PARAM_INVALID（不静默新建）")
        void rejectsMissingHeadcountRow() {
            when(occupancyMapper.selectCount(any())).thenReturn(0L);
            when(mapper.selectOne(any())).thenReturn(null);

            com.hris.common.exception.BizException e = assertThrows(
                    com.hris.common.exception.BizException.class,
                    () -> service.occupancy(req(1, "TXN004")));

            assertEquals(com.hris.common.exception.ErrorCode.PARAM_INVALID.getCode(), e.getCode());
        }

        @Test
        @DisplayName("delta 非 +1/-1 → PARAM_INVALID")
        void rejectsIllegalDelta() {
            com.hris.common.exception.BizException e = assertThrows(
                    com.hris.common.exception.BizException.class,
                    () -> service.occupancy(req(2, "TXN005")));

            assertEquals(com.hris.common.exception.ErrorCode.PARAM_INVALID.getCode(), e.getCode());
        }

        @Test
        @DisplayName("period 为空 → 按当前季度定位")
        void defaultsToCurrentPeriod() {
            OrgHeadcount existing = hc(5, 3);
            when(occupancyMapper.selectCount(any())).thenReturn(0L);
            when(mapper.selectOne(any())).thenReturn(existing);

            service.occupancy(new OccupancyRequest(100L, 1L, 2L, null, 1, "TXN006"));

            verify(mapper).updateById(existing);
        }
    }
}
