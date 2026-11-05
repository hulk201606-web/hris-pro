package com.hris.performance.service;

import com.hris.common.exception.BizException;
import com.hris.common.exception.ErrorCode;
import com.hris.performance.dto.CycleDtos.CycleCreateRequest;
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
 * CycleService 状态机单测（PRF-F-002）。
 *
 * <p>覆盖：白名单流转（DRAFT→ACTIVE→SCORING→CLOSED）、非法流转（跳级/回退/封板/未知目标）、
 * 各状态下配置写操作（update/delete）的拒绝，以及 update 不能夹带状态变更。</p>
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
    @DisplayName("transition 白名单流转")
    class Transition {

        @Test
        @DisplayName("DRAFT→ACTIVE：放行并落库")
        void draftToActive() {
            when(mapper.selectById(CYCLE_ID)).thenReturn(cycle("DRAFT"));
            when(mapper.updateById(any(Cycle.class))).thenReturn(1);

            var resp = service.transition(CYCLE_ID, "ACTIVE");

            assertEquals("ACTIVE", resp.status());
            verify(mapper).updateById(any(Cycle.class));
        }

        @Test
        @DisplayName("ACTIVE→SCORING：放行")
        void activeToScoring() {
            when(mapper.selectById(CYCLE_ID)).thenReturn(cycle("ACTIVE"));
            when(mapper.updateById(any(Cycle.class))).thenReturn(1);

            assertEquals("SCORING", service.transition(CYCLE_ID, "SCORING").status());
        }

        @Test
        @DisplayName("SCORING→CLOSED：放行")
        void scoringToClosed() {
            when(mapper.selectById(CYCLE_ID)).thenReturn(cycle("SCORING"));
            when(mapper.updateById(any(Cycle.class))).thenReturn(1);

            assertEquals("CLOSED", service.transition(CYCLE_ID, "CLOSED").status());
        }

        @Test
        @DisplayName("DRAFT→SCORING 跳级：PARAM_INVALID，不落库")
        void rejectsSkip() {
            when(mapper.selectById(CYCLE_ID)).thenReturn(cycle("DRAFT"));

            BizException e = assertThrows(BizException.class,
                    () -> service.transition(CYCLE_ID, "SCORING"));

            assertEquals(ErrorCode.PARAM_INVALID.getCode(), e.getCode());
            verify(mapper, never()).updateById(any(Cycle.class));
        }

        @Test
        @DisplayName("ACTIVE→DRAFT 回退：PARAM_INVALID")
        void rejectsRollback() {
            when(mapper.selectById(CYCLE_ID)).thenReturn(cycle("ACTIVE"));

            BizException e = assertThrows(BizException.class,
                    () -> service.transition(CYCLE_ID, "DRAFT"));

            assertEquals(ErrorCode.PARAM_INVALID.getCode(), e.getCode());
            verify(mapper, never()).updateById(any(Cycle.class));
        }

        @Test
        @DisplayName("CLOSED→任意：封板，PARAM_INVALID")
        void rejectsAfterClosed() {
            when(mapper.selectById(CYCLE_ID)).thenReturn(cycle("CLOSED"));

            BizException e = assertThrows(BizException.class,
                    () -> service.transition(CYCLE_ID, "ACTIVE"));

            assertEquals(ErrorCode.PARAM_INVALID.getCode(), e.getCode());
            verify(mapper, never()).updateById(any(Cycle.class));
        }

        @Test
        @DisplayName("未知目标状态：PARAM_INVALID")
        void rejectsUnknownTarget() {
            when(mapper.selectById(CYCLE_ID)).thenReturn(cycle("DRAFT"));

            BizException e = assertThrows(BizException.class,
                    () -> service.transition(CYCLE_ID, "PUBLISHED_X"));

            assertEquals(ErrorCode.PARAM_INVALID.getCode(), e.getCode());
        }
    }

    @Nested
    @DisplayName("配置写操作仅 DRAFT 开放")
    class ConfigGuard {

        @Test
        @DisplayName("DRAFT 可更新配置")
        void allowsDraftUpdate() {
            when(mapper.selectById(CYCLE_ID)).thenReturn(cycle("DRAFT"));
            when(mapper.updateById(any(Cycle.class))).thenReturn(1);

            service.update(CYCLE_ID, req("DRAFT"));

            verify(mapper).updateById(any(Cycle.class));
        }

        @Test
        @DisplayName("ACTIVE→改配置：PRF_ERROR，不落库")
        void rejectsActiveUpdate() {
            when(mapper.selectById(CYCLE_ID)).thenReturn(cycle("ACTIVE"));

            BizException e = assertThrows(BizException.class,
                    () -> service.update(CYCLE_ID, req("ACTIVE")));

            assertEquals(ErrorCode.PRF_ERROR.getCode(), e.getCode());
            verify(mapper, never()).updateById(any(Cycle.class));
        }

        @Test
        @DisplayName("CLOSED→改配置：PRF_ERROR（周期已关闭）")
        void rejectsClosedUpdate() {
            when(mapper.selectById(CYCLE_ID)).thenReturn(cycle("CLOSED"));

            BizException e = assertThrows(BizException.class,
                    () -> service.update(CYCLE_ID, req(null)));

            assertEquals(ErrorCode.PRF_ERROR.getCode(), e.getCode());
            verify(mapper, never()).updateById(any(Cycle.class));
        }

        @Test
        @DisplayName("update 夹带状态变更：PARAM_INVALID（必须走 transition）")
        void rejectsStatusChangeViaUpdate() {
            when(mapper.selectById(CYCLE_ID)).thenReturn(cycle("DRAFT"));

            BizException e = assertThrows(BizException.class,
                    () -> service.update(CYCLE_ID, req("ACTIVE")));

            assertEquals(ErrorCode.PARAM_INVALID.getCode(), e.getCode());
            verify(mapper, never()).updateById(any(Cycle.class));
        }

        @Test
        @DisplayName("仅 DRAFT 可删除")
        void rejectsDeleteWhenStarted() {
            when(mapper.selectById(CYCLE_ID)).thenReturn(cycle("ACTIVE"));

            assertThrows(BizException.class, () -> service.delete(CYCLE_ID));
            verify(mapper, never()).deleteById(any(Long.class));
        }

        @Test
        @DisplayName("创建强制 DRAFT：调用方指定 ACTIVE → PARAM_INVALID")
        void createForcesDraft() {
            BizException e = assertThrows(BizException.class, () -> service.create(
                    new CycleCreateRequest(1L, "2026H1", LocalDate.parse("2026-01-01"),
                            LocalDate.parse("2026-06-30"), "ACTIVE")));

            assertEquals(ErrorCode.PARAM_INVALID.getCode(), e.getCode());
            verify(mapper, never()).insert(any(Cycle.class));
        }
    }
}
