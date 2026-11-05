package com.hris.compensation.service;

import com.hris.common.exception.BizException;
import com.hris.common.exception.ErrorCode;
import com.hris.common.security.AuthContextHolder;
import com.hris.compensation.dto.PeriodLockDtos.PeriodLockResponse;
import com.hris.compensation.entity.PeriodLock;
import com.hris.compensation.mapper.PeriodLockMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 期间关账服务单测（CMP-F-020）：lock 重复拒绝、unlock 理由必填、
 * 期间级守卫拒绝、ACTIVE 唯一（应用层保证）。
 */
@ExtendWith(MockitoExtension.class)
class PeriodLockServiceTest {

    private static final String PERIOD = "2026-01";

    @Mock
    private PeriodLockMapper mapper;

    private PeriodLockService service;

    @BeforeEach
    void setUp() {
        service = new PeriodLockService(mapper);
        AuthContextHolder.setUserId(9L);
    }

    @AfterEach
    void tearDown() {
        AuthContextHolder.clear();
    }

    @Nested
    @DisplayName("lock")
    class Lock {

        @Test
        @DisplayName("无 ACTIVE 锁 → 落库 ACTIVE，locked_by 取当前用户")
        void locksWhenNoActiveLock() {
            when(mapper.selectCount(any())).thenReturn(0L);

            PeriodLockResponse resp = service.lock(PERIOD);

            assertEquals(PERIOD, resp.periodCode());
            assertEquals(PeriodLockService.STATUS_ACTIVE, resp.status());
            assertEquals(9L, resp.lockedBy());
            assertNotNull(resp.lockedAt());
            ArgumentCaptor<PeriodLock> captor = ArgumentCaptor.forClass(PeriodLock.class);
            verify(mapper).insert(captor.capture());
            assertEquals(9L, captor.getValue().getLockedBy());
        }

        @Test
        @DisplayName("已有 ACTIVE 锁 → PARAM_INVALID「已关账」，不落库")
        void rejectsDuplicateActiveLock() {
            when(mapper.selectCount(any())).thenReturn(1L);

            BizException e = assertThrows(BizException.class, () -> service.lock(PERIOD));

            assertEquals(ErrorCode.PARAM_INVALID.getCode(), e.getCode());
            verify(mapper, never()).insert(any(PeriodLock.class));
        }

        @Test
        @DisplayName("ACTIVE 唯一由应用层保证：lock 前置 selectCount(status=ACTIVE) 检查")
        void activeUniquenessCheckedBeforeInsert() {
            when(mapper.selectCount(any())).thenReturn(0L);

            service.lock(PERIOD);

            verify(mapper).selectCount(any());
            verify(mapper).insert(any(PeriodLock.class));
        }
    }

    @Nested
    @DisplayName("unlock")
    class Unlock {

        @Test
        @DisplayName("理由缺失 → PARAM_INVALID，不查锁")
        void rejectsMissingReason() {
            BizException e = assertThrows(BizException.class,
                    () -> service.unlock(PERIOD, "  "));

            assertEquals(ErrorCode.PARAM_INVALID.getCode(), e.getCode());
            verify(mapper, never()).selectOne(any());
        }

        @Test
        @DisplayName("理由不足 4 字 → PARAM_INVALID")
        void rejectsShortReason() {
            BizException e = assertThrows(BizException.class,
                    () -> service.unlock(PERIOD, "abc"));

            assertEquals(ErrorCode.PARAM_INVALID.getCode(), e.getCode());
        }

        @Test
        @DisplayName("无 ACTIVE 锁 → NOT_FOUND")
        void rejectsWhenNoActiveLock() {
            when(mapper.selectOne(any())).thenReturn(null);

            BizException e = assertThrows(BizException.class,
                    () -> service.unlock(PERIOD, "重开补录差额"));

            assertEquals(ErrorCode.NOT_FOUND.getCode(), e.getCode());
        }

        @Test
        @DisplayName("ACTIVE 锁 → 置 RELEASED 并记录 released_by/at/reason")
        void releasesActiveLockWithReason() {
            PeriodLock active = new PeriodLock();
            active.setId(1L);
            active.setPeriodCode(PERIOD);
            active.setStatus(PeriodLockService.STATUS_ACTIVE);
            active.setLockedBy(8L);
            when(mapper.selectOne(any())).thenReturn(active);
            when(mapper.updateById(any(PeriodLock.class))).thenReturn(1);

            PeriodLockResponse resp = service.unlock(PERIOD, "发现漏算补贴，重开补录");

            assertEquals(PeriodLockService.STATUS_RELEASED, resp.status());
            assertEquals(9L, resp.releasedBy());
            assertNotNull(resp.releasedAt());
            assertEquals("发现漏算补贴，重开补录", resp.unlockReason());
        }
    }

    @Nested
    @DisplayName("期间级守卫 ensurePeriodUnlocked")
    class Guard {

        @Test
        @DisplayName("有 ACTIVE 锁 → PARAM_INVALID「期间已关账，禁止变更」")
        void rejectsWhenLocked() {
            when(mapper.selectCount(any())).thenReturn(1L);

            BizException e = assertThrows(BizException.class,
                    () -> service.ensurePeriodUnlocked(PERIOD));

            assertEquals(ErrorCode.PARAM_INVALID.getCode(), e.getCode());
            assertEquals(true, e.getMessage().contains("期间已关账，禁止变更"));
        }

        @Test
        @DisplayName("无 ACTIVE 锁 / periodCode 为空 → 放行")
        void allowsWhenUnlocked() {
            when(mapper.selectCount(any())).thenReturn(0L);
            service.ensurePeriodUnlocked(PERIOD);
            service.ensurePeriodUnlocked(null);
        }
    }
}
