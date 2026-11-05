package com.hris.compensation.service;

import com.hris.common.exception.BizException;
import com.hris.common.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * PayrollRunService 状态机守卫单测（§8.5-3）。
 *
 * <p>批次进入 LOCK/PAID 锁账终态后，update/delete 一律拒绝——
 * 「锁后仅调整通道」由此强制；update 同时拦住把状态从 LOCK/PAID 改走的请求体。</p>
 */
@ExtendWith(MockitoExtension.class)
class PayrollRunServiceTest {

    private static final Long RUN_ID = 700001L;

    @Mock
    private com.hris.compensation.mapper.PayrollRunMapper mapper;

    private PayrollRunService service;

    @BeforeEach
    void setUp() {
        service = new PayrollRunService(mapper);
    }

    private com.hris.compensation.entity.PayrollRun run(String status) {
        com.hris.compensation.entity.PayrollRun e = new com.hris.compensation.entity.PayrollRun();
        e.setId(RUN_ID);
        e.setPeriodCode("2026-01");
        e.setPayGroupId(1L);
        e.setStatus(status);
        return e;
    }

    private com.hris.compensation.dto.PayrollRunDtos.PayrollRunUpdateRequest updateReq() {
        return new com.hris.compensation.dto.PayrollRunDtos.PayrollRunUpdateRequest(
                "2026-01", 1L, "INIT", 9L, null);
    }

    @Nested
    @DisplayName("update 守卫")
    class Update {

        @Test
        @DisplayName("LOCK 批次 → CMP_ERROR，不落库（含把状态改走的请求体）")
        void rejectsLockedUpdate() {
            when(mapper.selectById(RUN_ID)).thenReturn(run(PayrollRunService.STATUS_LOCK));

            BizException e = assertThrows(BizException.class,
                    () -> service.update(RUN_ID, updateReq()));

            assertEquals(ErrorCode.CMP_ERROR.getCode(), e.getCode());
            verify(mapper, never()).updateById(any(com.hris.compensation.entity.PayrollRun.class));
        }

        @Test
        @DisplayName("PAID 批次 → CMP_ERROR，不落库")
        void rejectsPaidUpdate() {
            when(mapper.selectById(RUN_ID)).thenReturn(run(PayrollRunService.STATUS_PAID));

            BizException e = assertThrows(BizException.class,
                    () -> service.update(RUN_ID, updateReq()));

            assertEquals(ErrorCode.CMP_ERROR.getCode(), e.getCode());
            verify(mapper, never()).updateById(any(com.hris.compensation.entity.PayrollRun.class));
        }

        @Test
        @DisplayName("INIT 批次 → 正常更新")
        void allowsDraftUpdate() {
            when(mapper.selectById(RUN_ID)).thenReturn(run("INIT"));
            when(mapper.updateById(any(com.hris.compensation.entity.PayrollRun.class)))
                    .thenReturn(1);

            var resp = service.update(RUN_ID, updateReq());

            assertEquals("INIT", resp.status());
            verify(mapper).updateById(any(com.hris.compensation.entity.PayrollRun.class));
        }
    }

    @Nested
    @DisplayName("delete 守卫")
    class Delete {

        @Test
        @DisplayName("LOCK 批次 → CMP_ERROR，不删除")
        void rejectsLockedDelete() {
            when(mapper.selectById(RUN_ID)).thenReturn(run(PayrollRunService.STATUS_LOCK));

            BizException e = assertThrows(BizException.class, () -> service.delete(RUN_ID));

            assertEquals(ErrorCode.CMP_ERROR.getCode(), e.getCode());
            verify(mapper, never()).deleteById(any(Long.class));
        }

        @Test
        @DisplayName("PAID 批次 → CMP_ERROR，不删除")
        void rejectsPaidDelete() {
            when(mapper.selectById(RUN_ID)).thenReturn(run(PayrollRunService.STATUS_PAID));

            BizException e = assertThrows(BizException.class, () -> service.delete(RUN_ID));

            assertEquals(ErrorCode.CMP_ERROR.getCode(), e.getCode());
            verify(mapper, never()).deleteById(any(Long.class));
        }

        @Test
        @DisplayName("INIT 批次 → 正常删除")
        void allowsDraftDelete() {
            when(mapper.selectById(RUN_ID)).thenReturn(run("INIT"));
            when(mapper.deleteById(RUN_ID)).thenReturn(1);

            service.delete(RUN_ID);

            verify(mapper).deleteById(RUN_ID);
        }
    }
}
