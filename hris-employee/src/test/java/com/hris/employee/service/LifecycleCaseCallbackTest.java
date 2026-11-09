package com.hris.employee.service;

import com.hris.common.exception.BizException;
import com.hris.common.exception.ErrorCode;
import com.hris.common.trace.TraceContext;
import com.hris.employee.entity.Employee;
import com.hris.employee.entity.LifecycleCase;
import com.hris.employee.enums.EmployeeStatus;
import com.hris.employee.enums.LifecycleType;
import com.hris.employee.enums.PendingTransType;
import com.hris.employee.integration.FlowIntegration;
import com.hris.employee.mapper.EmployeeMapper;
import com.hris.employee.mapper.LifecycleCaseMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * LifecycleCaseService 的 flow 审批回调单测（EMP-F-004 生命周期单，docs/19 §8.7-1）。
 *
 * <p>从 LifecycleCaseServiceTest 拆分而来（单文件 ≤300 行门禁），夹具与原类一致。
 * 回调是幂等入口：终态单据重复回调返回 changed=false，不重复落账。</p>
 */
@ExtendWith(MockitoExtension.class)
class LifecycleCaseCallbackTest {

    @Mock
    private LifecycleCaseMapper caseMapper;
    @Mock
    private EmployeeMapper employeeMapper;
    @Mock
    private PendingTransactionService pendingService;
    @Mock
    private EmployeeStateMachine stateMachine;
    @Mock
    private FlowIntegration flowIntegration;
    @Mock
    private SelfProfileService selfProfileService;

    private LifecycleCaseService service;

    @BeforeEach
    void setUp() {
        service = new LifecycleCaseService(caseMapper, employeeMapper, pendingService,
                stateMachine, flowIntegration, selfProfileService);
    }

    @AfterEach
    void clearTraceContext() {
        TraceContext.clear();
    }

    private Employee employee() {
        Employee e = new Employee();
        e.setId(100L);
        e.setEmpNo("E001");
        e.setStatus(EmployeeStatus.ACTIVE.getCode());
        return e;
    }

    @Nested
    @DisplayName("handleCallback：flow 审批回调（docs/19 §8.7-1），幂等入口")
    class HandleCallback {

        private LifecycleCase processingCase() {
            LifecycleCase c = new LifecycleCase();
            c.setId(3L);
            c.setEmpId(100L);
            c.setType(LifecycleType.TRANSFER.getCode());
            c.setBizKey("TRANSFER456");
            c.setPayload("{\"targetDeptId\":9}");
            c.setState("PROCESSING");
            c.setEffectDate(LocalDate.now().plusDays(30));
            return c;
        }

        @Test
        @DisplayName("单据已是终态（DONE）→ changed=false 直接返回成功，不重复落账（幂等）")
        void idempotentOnTerminalState() {
            LifecycleCase done = processingCase();
            done.setState("DONE");
            when(caseMapper.selectOne(any())).thenReturn(done);

            var resp = service.handleCallback(1L, "TRANSFER456", true, "重复回调");

            assertFalse(resp.changed());
            assertEquals("DONE", resp.state());
            verify(pendingService, never()).create(anyLong(), any(), any(), any());
            verify(caseMapper, never()).updateById(any(LifecycleCase.class));
        }

        @Test
        @DisplayName("单据已是终态（REJECTED）→ 同样幂等返回")
        void idempotentOnRejectedState() {
            LifecycleCase rejected = processingCase();
            rejected.setState("REJECTED");
            when(caseMapper.selectOne(any())).thenReturn(rejected);

            var resp = service.handleCallback(1L, "TRANSFER456", false, "重复回调");

            assertFalse(resp.changed());
            assertEquals("REJECTED", resp.state());
        }

        @Test
        @DisplayName("审批通过 → 生成待生效事务并置 DONE")
        void approvesProcessingCase() {
            LifecycleCase c = processingCase();
            when(caseMapper.selectOne(any())).thenReturn(c);
            when(pendingService.hasPending(100L)).thenReturn(false);

            var resp = service.handleCallback(1L, "TRANSFER456", true, "同意");

            assertTrue(resp.changed());
            assertEquals("DONE", resp.state());
            verify(pendingService).create(eq(100L), eq(PendingTransType.TRANSFER), any(), any());
        }

        @Test
        @DisplayName("审批驳回 → 置 REJECTED 终态，不生成事务")
        void rejectsProcessingCase() {
            LifecycleCase c = processingCase();
            when(caseMapper.selectOne(any())).thenReturn(c);

            var resp = service.handleCallback(1L, "TRANSFER456", false, "不同意");

            assertTrue(resp.changed());
            assertEquals("REJECTED", resp.state());
            assertEquals("REJECTED", c.getState());
            verify(pendingService, never()).create(anyLong(), any(), any(), any());
            verify(caseMapper).updateById(c);
        }

        @Test
        @DisplayName("单据不存在 → NOT_FOUND")
        void caseNotFound() {
            when(caseMapper.selectOne(any())).thenReturn(null);

            BizException e = assertThrows(BizException.class,
                    () -> service.handleCallback(1L, "NOPE", true, null));

            assertEquals(ErrorCode.NOT_FOUND.getCode(), e.getCode());
        }

        @Test
        @DisplayName("租户缺失（body 与上下文均无）→ TENANT_INVALID")
        void tenantMissing() {
            BizException e = assertThrows(BizException.class,
                    () -> service.handleCallback(null, "TRANSFER456", true, null));

            assertEquals(ErrorCode.TENANT_INVALID.getCode(), e.getCode());
            verify(caseMapper, never()).selectOne(any());
        }
    }
}
