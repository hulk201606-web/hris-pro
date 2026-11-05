package com.hris.employee.service;

import com.hris.common.exception.BizException;
import com.hris.common.trace.TraceContext;
import com.hris.employee.entity.Employee;
import com.hris.employee.entity.PendingTransaction;
import com.hris.employee.enums.EmployeeStatus;
import com.hris.employee.enums.PendingTransType;
import com.hris.employee.integration.AccountIntegration;
import com.hris.employee.mapper.EmployeeMapper;
import com.hris.employee.mapper.PendingTransactionMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * PendingTransactionService 的 ThreadLocal 清理守卫（独立成文件）。
 *
 * <p>TraceContext 基于 ThreadLocal，而落账由调度线程批量调用：若applySingle/applyDue
 * 结束后不清理，线程复用会把A 租户的 tenantId 带给 B 租户的落账，属跨租户数据串号。
 * 该风险与业务分支无关、且必须每个用例后clear，故独立成类以避免与业务用例互相掩盖。</p>
 */
@ExtendWith(MockitoExtension.class)
class PendingTransactionTraceContextTest {

    @Mock
    private PendingTransactionMapper pendingMapper;
    @Mock
    private EmployeeMapper employeeMapper;
    @Mock
    private EmployeeStateMachine stateMachine;
    @Mock
    private EmploymentService employmentService;
    @Mock
    private AccountIntegration accountIntegration;
    @Mock
    private com.hris.employee.integration.HeadcountIntegration headcountIntegration;

    private PendingTransactionService service;

    @BeforeEach
    void setUp() {
        service = new PendingTransactionService(pendingMapper, employeeMapper, stateMachine,
                employmentService, accountIntegration, headcountIntegration);
    }

    @AfterEach
    void clearTraceContext() {
        TraceContext.clear();
    }

    private PendingTransaction tx(PendingTransType type, String state, String payload) {
        PendingTransaction t = new PendingTransaction();
        t.setEmpId(100L);
        t.setTransNo("TXN001");
        t.setTransType(type == null ? null : type.getCode());
        t.setState(state);
        t.setPayload(payload);
        t.setEffectDate(LocalDate.now());
        return t;
    }

    private Employee employee() {
        Employee e = new Employee();
        e.setId(100L);
        e.setEmpNo("E001");
        e.setStatus(EmployeeStatus.PROBATION.getCode());
        return e;
    }


    @Nested
    @DisplayName("ThreadLocal 清理：调度线程复用不得串租户")
    class TraceContextCleanup {

        @Test
        @DisplayName("applySingle 结束后 tenantId 被清理为 null")
        void clearsTenantAfterApplySingle() {
            PendingTransaction t = tx(PendingTransType.REGULAR, "PENDING", "{}");
            when(pendingMapper.selectOne(any())).thenReturn(t);
            when(employeeMapper.selectById(100L)).thenReturn(employee());

            service.applySingle(1L, "TXN001");

            assertNull(TraceContext.getTenantId(),
                    "落账结束必须清理 ThreadLocal，否则调度线程复用会串租户");
            assertNull(TraceContext.getUserId());
        }

        @Test
        @DisplayName("applyDue 抛异常时仍清理 ThreadContext（finally 分支）")
        void clearsTenantEvenOnException() {
            PendingTransaction t = tx(PendingTransType.REGULAR, "PENDING", "{}");
            when(pendingMapper.selectOne(any())).thenReturn(t);
            when(employeeMapper.selectById(100L)).thenReturn(null);

            assertThrows(BizException.class, () -> service.applySingle(9L, "TXN001"));

            assertNull(TraceContext.getTenantId(),
                    "异常路径也必须清理，否则失败批次会污染后续调度");
        }
    }
}
