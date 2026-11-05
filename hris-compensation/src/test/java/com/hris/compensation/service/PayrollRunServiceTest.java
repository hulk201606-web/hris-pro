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

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * PayrollRunService 状态机守卫单测（§8.5-3 + docs/19 §9 审批发起侧接入）。
 *
 * <p>批次进入 LOCK/PAID 锁账终态后，update/delete 一律拒绝——
 * 「锁后仅调整通道」由此强制。submit 仅 INIT/TRIAL/CALC 可提交（→REVIEW 并发起流程），
 * fail-open：flow 不可用时批次保留 REVIEW 返回成功。</p>
 */
@ExtendWith(MockitoExtension.class)
class PayrollRunServiceTest {

    private static final Long RUN_ID = 700001L;

    @Mock
    private com.hris.compensation.mapper.PayrollRunMapper mapper;
    @Mock
    private com.hris.compensation.mapper.PayrollDetailMapper detailMapper;
    @Mock
    private FlowIntegration flowIntegration;

    private PayrollRunService service;

    @BeforeEach
    void setUp() {
        service = new PayrollRunService(mapper, detailMapper, flowIntegration);
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
                "2026-01", 1L, 9L, null);
    }

    @Nested
    @DisplayName("update 守卫")
    class Update {

        @Test
        @DisplayName("LOCK 批次 → CMP_ERROR，不落库")
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

    @Nested
    @DisplayName("submit 守卫与状态迁移")
    class Submit {

        @Test
        @DisplayName("INIT 批次 → 迁移 REVIEW 并发起流程，bizKey=id 十进制串")
        void submitsInitToReview() {
            when(mapper.selectById(RUN_ID)).thenReturn(run("INIT"));
            when(mapper.updateById(any(com.hris.compensation.entity.PayrollRun.class))).thenReturn(1);
            when(detailMapper.selectCount(any())).thenReturn(3L);
            when(flowIntegration.start(eq("CMP_PAYROLL"), eq("700001"), any())).thenReturn("inst-1");

            var resp = service.submit(RUN_ID);

            assertEquals("REVIEW", resp.status());
            verify(flowIntegration).start(eq("CMP_PAYROLL"), eq(String.valueOf(RUN_ID)), any());
        }

        @Test
        @DisplayName("TRIAL 批次 → 可提交，迁移 REVIEW")
        void submitsTrialToReview() {
            when(mapper.selectById(RUN_ID)).thenReturn(run("TRIAL"));
            when(mapper.updateById(any(com.hris.compensation.entity.PayrollRun.class))).thenReturn(1);
            when(flowIntegration.start(eq("CMP_PAYROLL"), eq("700001"), any())).thenReturn("inst-1");

            assertEquals("REVIEW", service.submit(RUN_ID).status());
        }

        @Test
        @DisplayName("REVIEW 批次重复 submit → CMP_ERROR，不再发起流程")
        void rejectsDuplicateSubmit() {
            when(mapper.selectById(RUN_ID)).thenReturn(run(PayrollRunService.STATUS_REVIEW));

            BizException e = assertThrows(BizException.class, () -> service.submit(RUN_ID));

            assertEquals(ErrorCode.CMP_ERROR.getCode(), e.getCode());
            verify(flowIntegration, never()).start(any(), any(), any());
        }

        @Test
        @DisplayName("APPROVE/PAID/LOCK 批次 → CMP_ERROR 拒绝")
        void rejectsTerminalStates() {
            for (String status : new String[]{"APPROVE", PayrollRunService.STATUS_PAID, PayrollRunService.STATUS_LOCK}) {
                when(mapper.selectById(RUN_ID)).thenReturn(run(status));

                BizException e = assertThrows(BizException.class, () -> service.submit(RUN_ID));

                assertEquals(ErrorCode.CMP_ERROR.getCode(), e.getCode());
            }
            verify(flowIntegration, never()).start(any(), any(), any());
        }

        @Test
        @DisplayName("fail-open：flow 降级（发起失败返回 null）→ 批次保留 REVIEW 返回成功")
        void failsOpenWhenFlowUnavailable() {
            when(mapper.selectById(RUN_ID)).thenReturn(run("INIT"));
            when(mapper.updateById(any(com.hris.compensation.entity.PayrollRun.class))).thenReturn(1);
            when(detailMapper.selectCount(any())).thenReturn(0L);
            when(flowIntegration.start(eq("CMP_PAYROLL"), eq("700001"), any())).thenReturn(null);

            var resp = service.submit(RUN_ID);

            assertEquals("REVIEW", resp.status());
            verify(mapper).updateById(any(com.hris.compensation.entity.PayrollRun.class));
        }

        @Test
        @DisplayName("payload 仅含非敏感摘要：期间/薪资组/人数")
        void payloadContainsNonSensitiveSummaryOnly() {
            when(mapper.selectById(RUN_ID)).thenReturn(run("INIT"));
            when(mapper.updateById(any(com.hris.compensation.entity.PayrollRun.class))).thenReturn(1);
            when(detailMapper.selectCount(any())).thenReturn(5L);
            when(flowIntegration.start(any(), any(), any())).thenReturn("inst-1");

            service.submit(RUN_ID);

            verify(flowIntegration).start(eq("CMP_PAYROLL"), eq("700001"),
                    eq(Map.of("periodCode", "2026-01", "payGroupId", 1L, "employeeCount", 5L)));
        }
    }
}
