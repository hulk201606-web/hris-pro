package com.hris.employee.service;

import com.hris.common.exception.BizException;
import com.hris.common.exception.ErrorCode;
import com.hris.common.trace.TraceContext;
import com.hris.employee.entity.Employee;
import com.hris.employee.entity.LifecycleCase;
import com.hris.employee.entity.PendingTransaction;
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
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * LifecycleCaseService 单元测试（EMP-F-004/008/009/011 生命周期单编排）。
 *
 * <p>三道守卫：①防重复发起——存在未生效事务或进行中单据时拒绝新建
 * ②防重复审批——单据非PROCESSING 时拒绝再次审批
 * ③按生效日决定是否立即落账（effectDate≤今天 →立即 applySingle）。
 * ①②是幂等性守门砖，重复提交会造成重复入职/重复离职。</p>
 */
@ExtendWith(MockitoExtension.class)
class LifecycleCaseServiceTest {

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

    private LifecycleCaseService service;

    @BeforeEach
    void setUp() {
        service = new LifecycleCaseService(caseMapper, employeeMapper, pendingService,
                stateMachine, flowIntegration);
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
    @DisplayName("createCase 守卫 ①：防重复发起")
    class DuplicateGuard {

        @Test
        @DisplayName("存在未生效事务 → EMP_ERROR，不落单（防重复入职/调动）")
        void rejectsWhenPendingTransactionExists() {
            when(pendingService.hasPending(100L)).thenReturn(true);

            BizException e = assertThrows(BizException.class, () -> service.createCase(
                    100L, LifecycleType.ONBOARD, LocalDate.now().plusDays(7), Map.of(), "入职"));

            assertEquals(ErrorCode.EMP_ERROR.getCode(), e.getCode());
            verify(caseMapper, never()).insert(any(LifecycleCase.class));
        }

        @Test
        @DisplayName("已有进行中单据 → EMP_ERROR「请勿重复发起」，不落单")
        void rejectsWhenProcessingCaseExists() {
            when(pendingService.hasPending(100L)).thenReturn(false);
            when(caseMapper.selectCount(any())).thenReturn(1L);

            BizException e = assertThrows(BizException.class, () -> service.createCase(
                    100L, LifecycleType.TRANSFER, LocalDate.now(), Map.of(), "调动"));

            assertEquals(ErrorCode.EMP_ERROR.getCode(), e.getCode());
            verify(caseMapper, never()).insert(any(LifecycleCase.class));
        }
    }

    @Nested
    @DisplayName("createCase：正常落单")
    class Create {

        @Test
        @DisplayName("落单 → 状态 PROCESSING、类型落 code、载荷序列化")
        void createsProcessingCase() {
            when(pendingService.hasPending(100L)).thenReturn(false);
            when(caseMapper.selectCount(any())).thenReturn(0L);
            when(flowIntegration.start(anyString(), anyString(), any(), any())).thenReturn("FLOW001");

            LifecycleCase c = service.createCase(100L, LifecycleType.ONBOARD,
                    LocalDate.now().plusDays(7), Map.of("deptId", 2), "入职办理");

            assertEquals("PROCESSING", c.getState());
            assertEquals(LifecycleType.ONBOARD.getCode(), c.getType());
            assertTrue(c.getPayload().contains("deptId"));
        }

        @Test
        @DisplayName("flow 降级返回 null → 单据仍落库并回写 procInstId=null（仅落单不阻断）")
        void degradesWhenFlowUnavailable() {
            when(pendingService.hasPending(100L)).thenReturn(false);
            when(caseMapper.selectCount(any())).thenReturn(0L);
            when(flowIntegration.start(anyString(), anyString(), any(), any())).thenReturn(null);

            LifecycleCase c = service.createCase(100L, LifecycleType.REGULAR,
                    LocalDate.now(), Map.of(), "转正");

            assertEquals("PROCESSING", c.getState());
            verify(caseMapper).updateById(c);
        }
    }

    @Nested
    @DisplayName("approve 守卫 ②：防重复审批")
    class Approve {

        private LifecycleCase processingCase() {
            LifecycleCase c = new LifecycleCase();
            c.setId(1L);
            c.setEmpId(100L);
            c.setType(LifecycleType.TRANSFER.getCode());
            c.setBizKey("TRANSFER123");
            c.setPayload("{\"targetDeptId\":9}");
            c.setState("PROCESSING");
            c.setEffectDate(LocalDate.now().plusDays(30));
            return c;
        }

        @Test
        @DisplayName("单据不存在 → NOT_FOUND")
        void caseNotFound() {
            when(caseMapper.selectOne(any())).thenReturn(null);

            BizException e = assertThrows(BizException.class,
                    () -> service.approve(1L, "NOPE"));

            assertEquals(ErrorCode.NOT_FOUND.getCode(), e.getCode());
        }

        @Test
        @DisplayName("单据已被审批（DONE）→ EMP_ERROR，不重复生成事务（幂等）")
        void rejectsAlreadyApproved() {
            LifecycleCase done = processingCase();
            done.setState("DONE");
            when(caseMapper.selectOne(any())).thenReturn(done);

            BizException e = assertThrows(BizException.class,
                    () -> service.approve(1L, "TRANSFER123"));

            assertEquals(ErrorCode.EMP_ERROR.getCode(), e.getCode());
            verify(pendingService, never()).create(anyLong(), any(), any(), any());
        }

        @Test
        @DisplayName("审批通过 → 生成待生效事务并把单据置 DONE")
        void generatesPendingTransaction() {
            LifecycleCase c = processingCase();
            when(caseMapper.selectOne(any())).thenReturn(c);
            when(pendingService.hasPending(100L)).thenReturn(false);

            service.approve(1L, "TRANSFER123");

            verify(pendingService).create(eq(100L), eq(PendingTransType.TRANSFER),
                    eq(LocalDate.now().plusDays(30)), any());
            assertEquals("DONE", c.getState(), "审批通过后单据应置 DONE，防重复审批");
            verify(caseMapper).updateById(c);
        }

        @Test
        @DisplayName("审批时又出现未生效事务 → EMP_ERROR，不覆盖已有事务")
        void rejectsWhenConcurrentPendingAppears() {
            when(caseMapper.selectOne(any())).thenReturn(processingCase());
            when(pendingService.hasPending(100L)).thenReturn(true);

            BizException e = assertThrows(BizException.class,
                    () -> service.approve(1L, "TRANSFER123"));

            assertEquals(ErrorCode.EMP_ERROR.getCode(), e.getCode());
            verify(pendingService, never()).create(anyLong(), any(), any(), any());
        }
    }

    @Nested
    @DisplayName("approve：离职类型的状态编排与立即落账")
    class ApproveResign {

        private LifecycleCase resignCase(LocalDate effectDate) {
            LifecycleCase c = new LifecycleCase();
            c.setId(2L);
            c.setEmpId(100L);
            c.setType(LifecycleType.RESIGN.getCode());
            c.setBizKey("RESIGN123");
            c.setPayload("{\"resignType\":\"VOLUNTARY\"}");
            c.setState("PROCESSING");
            c.setEffectDate(effectDate);
            return c;
        }

        @Test
        @DisplayName("离职审批通过 → 先把员工推进到离职中（RESIGNING）")
        void transitionsToResigning() {
            when(caseMapper.selectOne(any())).thenReturn(resignCase(LocalDate.now().plusDays(30)));
            when(employeeMapper.selectById(100L)).thenReturn(employee());
            when(pendingService.hasPending(100L)).thenReturn(false);

            service.approve(1L, "RESIGN123");

            verify(stateMachine).transition(any(Employee.class), eq(EmployeeStatus.RESIGNING),
                    anyString());
        }

        @Test
        @DisplayName("离职员工记录缺失 → NOT_FOUND，不推进状态")
        void employeeMissingDuringResignApproval() {
            when(caseMapper.selectOne(any())).thenReturn(resignCase(LocalDate.now().plusDays(30)));
            when(employeeMapper.selectById(100L)).thenReturn(null);

            BizException e = assertThrows(BizException.class,
                    () -> service.approve(1L, "RESIGN123"));

            assertEquals(ErrorCode.NOT_FOUND.getCode(), e.getCode());
            verify(stateMachine, never()).transition(any(Employee.class), any(), anyString());
        }

        @Test
        @DisplayName("生效日已到 → 生成事务后立即落账（applySingle 用新建事务的 transNo）")
        void appliesImmediatelyWhenEffectDateReached() {
            PendingTransaction tx = new PendingTransaction();
            tx.setTransNo("TXN-RESIGN-001");
            when(caseMapper.selectOne(any())).thenReturn(resignCase(LocalDate.now()));
            when(employeeMapper.selectById(100L)).thenReturn(employee());
            when(pendingService.hasPending(100L)).thenReturn(false);
            when(pendingService.create(eq(100L), eq(PendingTransType.RESIGN), any(), any()))
                    .thenReturn(tx);

            service.approve(1L, "RESIGN123");

            verify(pendingService).applySingle(eq(1L), eq("TXN-RESIGN-001"));
            verify(caseMapper).updateById(any(LifecycleCase.class));
        }

        @Test
        @DisplayName("生效日未到 → 只生成事务，不立即落账")
        void defersWhenEffectDateInFuture() {
            when(caseMapper.selectOne(any())).thenReturn(resignCase(LocalDate.now().plusDays(30)));
            when(employeeMapper.selectById(100L)).thenReturn(employee());
            when(pendingService.hasPending(100L)).thenReturn(false);

            service.approve(1L, "RESIGN123");

            verify(pendingService).create(eq(100L), eq(PendingTransType.RESIGN), any(), any());
            verify(pendingService, never()).applySingle(anyLong(), anyString());
        }
    }
}