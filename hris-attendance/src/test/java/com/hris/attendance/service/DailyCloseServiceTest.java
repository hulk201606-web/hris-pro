package com.hris.attendance.service;

import com.hris.attendance.dto.ClosingDtos.DailyCloseResponse;
import com.hris.attendance.entity.AttDailyClose;
import com.hris.attendance.mapper.AttDailyCloseMapper;
import com.hris.common.exception.BizException;
import com.hris.common.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 日考勤月结锁单测（批11#19）。
 *
 * <p>锁定重复、解锁理由必填、重算守卫三条语义是「月结后历史不可改」的入口约束，
 * 变更必须同步本测试。</p>
 */
@ExtendWith(MockitoExtension.class)
class DailyCloseServiceTest {

    @Mock
    private AttDailyCloseMapper closeMapper;

    private DailyCloseService service;

    @BeforeEach
    void setUp() {
        service = new DailyCloseService(closeMapper);
    }

    private AttDailyClose active(String period) {
        AttDailyClose close = new AttDailyClose();
        close.setId(800001L);
        close.setPeriod(period);
        close.setStatus(AttDailyClose.STATUS_ACTIVE);
        return close;
    }

    @Nested
    @DisplayName("锁定")
    class Lock {

        @Test
        @DisplayName("无生效锁 → 新建 ACTIVE 记录")
        void lockCreatesActiveRow() {
            when(closeMapper.selectOne(any())).thenReturn(null);
            when(closeMapper.insert(any(AttDailyClose.class))).thenReturn(1);

            DailyCloseResponse resp = service.lock("2026-10");

            assertEquals("2026-10", resp.period());
            assertEquals(AttDailyClose.STATUS_ACTIVE, resp.status());
            assertNotNull(resp.closedAt(), "锁定时间必填（uk 组成部分）");
            verify(closeMapper).insert(any(AttDailyClose.class));
        }

        @Test
        @DisplayName("期间已有 ACTIVE 锁 → 拒绝重复锁定")
        void rejectsDuplicateLock() {
            when(closeMapper.selectOne(any())).thenReturn(active("2026-10"));

            BizException e = assertThrows(BizException.class, () -> service.lock("2026-10"));

            assertEquals(ErrorCode.ATT_ERROR.getCode(), e.getCode());
            assertEquals("期间 2026-10 已月结，不可重复锁定", e.getMessage());
            verify(closeMapper, never()).insert(any(AttDailyClose.class));
        }

        @Test
        @DisplayName("期间格式非法 → 10001")
        void rejectsInvalidPeriod() {
            BizException e = assertThrows(BizException.class, () -> service.lock("2026-13"));

            assertEquals(ErrorCode.PARAM_INVALID.getCode(), e.getCode());
        }
    }

    @Nested
    @DisplayName("解锁")
    class Unlock {

        @Test
        @DisplayName("理由缺失 → 10001，不改状态")
        void rejectsBlankReason() {
            BizException e = assertThrows(BizException.class,
                    () -> service.unlock("2026-10", "  "));

            assertEquals(ErrorCode.PARAM_INVALID.getCode(), e.getCode());
            assertEquals("解锁理由不能为空", e.getMessage());
            verify(closeMapper, never()).updateById(any(AttDailyClose.class));
        }

        @Test
        @DisplayName("有 ACTIVE 锁 + 合法理由 → 置 RELEASED 并记录解锁人/时间/理由")
        void releasesActiveLock() {
            when(closeMapper.selectOne(any())).thenReturn(active("2026-10"));
            when(closeMapper.updateById(any(AttDailyClose.class))).thenReturn(1);

            DailyCloseResponse resp = service.unlock("2026-10", "考勤数据修正");

            assertEquals(AttDailyClose.STATUS_RELEASED, resp.status());
            assertEquals("考勤数据修正", resp.releaseReason());
            assertNotNull(resp.releasedAt());
        }

        @Test
        @DisplayName("无 ACTIVE 锁 → 拒绝解锁")
        void rejectsUnlockWithoutActive() {
            when(closeMapper.selectOne(any())).thenReturn(null);

            BizException e = assertThrows(BizException.class,
                    () -> service.unlock("2026-10", "重复操作"));

            assertEquals(ErrorCode.ATT_ERROR.getCode(), e.getCode());
        }
    }

    @Nested
    @DisplayName("重算守卫")
    class Guard {

        @Test
        @DisplayName("期间 ACTIVE → 抛「期间已月结」")
        void rejectsWhenPeriodLocked() {
            when(closeMapper.selectOne(any())).thenReturn(active("2026-10"));

            BizException e = assertThrows(BizException.class,
                    () -> service.ensureNotClosed(LocalDate.parse("2026-10-16")));

            assertEquals(ErrorCode.ATT_ERROR.getCode(), e.getCode());
            assertEquals("期间 2026-10 已月结，不可重算；差异请走月结调整通道", e.getMessage());
        }

        @Test
        @DisplayName("期间无锁 → 放行")
        void allowsWhenNotLocked() {
            when(closeMapper.selectOne(any())).thenReturn(null);

            service.ensureNotClosed(LocalDate.parse("2026-10-16"));
        }
    }
}
