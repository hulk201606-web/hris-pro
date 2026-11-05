package com.hris.performance.service;

import com.hris.common.exception.BizException;
import com.hris.common.exception.ErrorCode;
import com.hris.performance.dto.CycleDtos.CycleUpdateRequest;
import com.hris.performance.entity.Cycle;
import com.hris.performance.mapper.CycleMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * CycleService 状态机守卫单测（§8.5-4）。
 *
 * <p>PUBLISHED/CLOSED 为锁定态：PUBLISHED 仅允许 PUBLISHED→CLOSED 一个收尾迁移，
 * CLOSED 完全封板，其余状态自由编辑。</p>
 */
@ExtendWith(MockitoExtension.class)
class CycleServiceTest {

    private static final Long CYCLE_ID = 600001L;

    @Mock
    private CycleMapper mapper;

    private CycleService service;

    @BeforeEach
    void setUp() {
        service = new CycleService(mapper);
    }

    private Cycle cycle(String status) {
        Cycle c = new Cycle();
        c.setId(CYCLE_ID);
        c.setPlanId(1L);
        c.setPeriodCode("2026H1");
        c.setStartDate(LocalDate.parse("2026-01-01"));
        c.setEndDate(LocalDate.parse("2026-06-30"));
        c.setStatus(status);
        return c;
    }

    private CycleUpdateRequest req(String status) {
        return new CycleUpdateRequest(1L, "2026H1",
                LocalDate.parse("2026-01-01"), LocalDate.parse("2026-06-30"), status);
    }

    @Nested
    @DisplayName("update 周期锁守卫")
    class UpdateGuard {

        @Test
        @DisplayName("PUBLISHED → CLOSED 收尾迁移：唯一放行")
        void allowsPublishedToClosed() {
            when(mapper.selectById(CYCLE_ID)).thenReturn(cycle(CycleService.STATUS_PUBLISHED));
            when(mapper.updateById(any(Cycle.class))).thenReturn(1);

            var resp = service.update(CYCLE_ID, req(CycleService.STATUS_CLOSED));

            assertEquals(CycleService.STATUS_CLOSED, resp.status());
            verify(mapper).updateById(any(Cycle.class));
        }

        @Test
        @DisplayName("PUBLISHED → ACTIVE：拒绝（发布后仅允许关闭）")
        void rejectsPublishedToOther() {
            when(mapper.selectById(CYCLE_ID)).thenReturn(cycle(CycleService.STATUS_PUBLISHED));

            BizException e = assertThrows(BizException.class,
                    () -> service.update(CYCLE_ID, req("ACTIVE")));

            assertEquals(ErrorCode.PRF_ERROR.getCode(), e.getCode());
            verify(mapper, never()).updateById(any(Cycle.class));
        }

        @Test
        @DisplayName("CLOSED → 任何状态：拒绝（完全封板）")
        void rejectsClosedUpdate() {
            when(mapper.selectById(CYCLE_ID)).thenReturn(cycle(CycleService.STATUS_CLOSED));

            BizException e = assertThrows(BizException.class,
                    () -> service.update(CYCLE_ID, req(CycleService.STATUS_CLOSED)));

            assertEquals(ErrorCode.PRF_ERROR.getCode(), e.getCode());
            verify(mapper, never()).updateById(any(Cycle.class));
        }

        @Test
        @DisplayName("DRAFT → 自由编辑")
        void allowsDraftUpdate() {
            when(mapper.selectById(CYCLE_ID)).thenReturn(cycle("DRAFT"));
            when(mapper.updateById(any(Cycle.class))).thenReturn(1);

            service.update(CYCLE_ID, req("ACTIVE"));

            verify(mapper).updateById(any(Cycle.class));
        }
    }
}
