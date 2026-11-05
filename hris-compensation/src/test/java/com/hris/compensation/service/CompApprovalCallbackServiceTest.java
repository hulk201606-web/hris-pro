package com.hris.compensation.service;

import com.hris.common.exception.BizException;
import com.hris.common.exception.ErrorCode;
import com.hris.compensation.dto.ApprovalDtos.ApprovalCallbackResponse;
import com.hris.compensation.entity.PayrollRun;
import com.hris.compensation.mapper.PayrollRunMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * CompApprovalCallbackService 单元测试（docs/19 §9 遗留项四域接入）：
 * CMP_PAYROLL 状态迁移幂等 + CMP_ADJUST 无载体 changed=false。
 */
@ExtendWith(MockitoExtension.class)
class CompApprovalCallbackServiceTest {

    @Mock
    private PayrollRunMapper payrollRunMapper;

    private CompApprovalCallbackService service;

    @BeforeEach
    void setUp() {
        service = new CompApprovalCallbackService(payrollRunMapper);
    }

    private PayrollRun run(String status) {
        PayrollRun e = new PayrollRun();
        e.setId(88L);
        e.setStatus(status);
        return e;
    }

    @Nested
    @DisplayName("CMP_PAYROLL：REVIEW → APPROVE，幂等")
    class PayrollCallback {

        @Test
        @DisplayName("REVIEW + approve → APPROVE")
        void approvesToApprove() {
            when(payrollRunMapper.selectById(88L)).thenReturn(run("REVIEW"));

            ApprovalCallbackResponse resp = service.handleCallback("CMP_PAYROLL", "88", true);

            assertTrue(resp.changed());
            assertEquals("APPROVE", resp.status());
            verify(payrollRunMapper).updateById(any(PayrollRun.class));
        }

        @Test
        @DisplayName("REVIEW + reject → 无载体维持 REVIEW，changed=false")
        void rejectKeepsReview() {
            when(payrollRunMapper.selectById(88L)).thenReturn(run("REVIEW"));

            ApprovalCallbackResponse resp = service.handleCallback("CMP_PAYROLL", "88", false);

            assertFalse(resp.changed());
            assertEquals("REVIEW", resp.status());
            verify(payrollRunMapper, never()).updateById(any(PayrollRun.class));
        }

        @Test
        @DisplayName("已 APPROVE（终态重复回调）→ changed=false，不重复变更")
        void idempotentOnApprove() {
            when(payrollRunMapper.selectById(88L)).thenReturn(run("APPROVE"));

            ApprovalCallbackResponse resp = service.handleCallback("CMP_PAYROLL", "88", true);

            assertFalse(resp.changed());
            assertEquals("APPROVE", resp.status());
            verify(payrollRunMapper, never()).updateById(any(PayrollRun.class));
        }

        @Test
        @DisplayName("已 PAID/LOCK（终态重复回调）→ changed=false")
        void idempotentOnPaid() {
            when(payrollRunMapper.selectById(88L)).thenReturn(run("PAID"));

            ApprovalCallbackResponse resp = service.handleCallback("CMP_PAYROLL", "88", true);

            assertFalse(resp.changed());
            verify(payrollRunMapper, never()).updateById(any(PayrollRun.class));
        }

        @Test
        @DisplayName("INIT（未达复核阶段）→ changed=false")
        void skipsInit() {
            when(payrollRunMapper.selectById(88L)).thenReturn(run("INIT"));

            ApprovalCallbackResponse resp = service.handleCallback("CMP_PAYROLL", "88", true);

            assertFalse(resp.changed());
            verify(payrollRunMapper, never()).updateById(any(PayrollRun.class));
        }

        @Test
        @DisplayName("批次不存在 → NOT_FOUND")
        void notFound() {
            when(payrollRunMapper.selectById(88L)).thenReturn(null);

            BizException e = assertThrows(BizException.class,
                    () -> service.handleCallback("CMP_PAYROLL", "88", true));

            assertEquals(ErrorCode.NOT_FOUND.getCode(), e.getCode());
        }

        @Test
        @DisplayName("bizKey 非数字 → PARAM_INVALID")
        void invalidBizKey() {
            BizException e = assertThrows(BizException.class,
                    () -> service.handleCallback("CMP_PAYROLL", "RUN-2026-01", true));

            assertEquals(ErrorCode.PARAM_INVALID.getCode(), e.getCode());
        }
    }

    @Test
    @DisplayName("CMP_ADJUST 无审批载体 → changed=false，不查询任何数据")
    void adjustHasNoCarrier() {
        ApprovalCallbackResponse resp = service.handleCallback("CMP_ADJUST", "1", true);

        assertFalse(resp.changed());
        verify(payrollRunMapper, never()).selectById(any());
    }

    @Test
    @DisplayName("未知 bizType → changed=false，不抛异常")
    void unknownBizType() {
        ApprovalCallbackResponse resp = service.handleCallback("CMP_UNKNOWN", "1", true);

        assertFalse(resp.changed());
        verify(payrollRunMapper, never()).selectById(any());
    }
}
