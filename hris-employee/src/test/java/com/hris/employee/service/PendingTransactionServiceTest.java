package com.hris.employee.service;

import com.hris.common.exception.BizException;
import com.hris.common.exception.ErrorCode;
import com.hris.employee.entity.Employee;
import com.hris.employee.entity.Employment;
import com.hris.employee.entity.PendingTransaction;
import com.hris.employee.enums.EmployeeStatus;
import com.hris.employee.enums.PendingTransType;
import com.hris.employee.integration.AccountIntegration;
import com.hris.employee.mapper.EmployeeMapper;
import com.hris.employee.mapper.PendingTransactionMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * PendingTransactionService 单元测试（docs/03-02 §2 幂等落账器）。
 *
 * <p>落账是"把审批结果真正写进业务数据"的唯一入口，三道守卫：①幂等——非 PENDING 直接跳过，
 * 重复调度不会二次落账②未知事务类型拒绝落账（防脏数据静默吞掉）③ThreadLocal 必须在 finally
 * 清理，否则调度线程复用会导致租户串号。</p>
 */
@ExtendWith(MockitoExtension.class)
class PendingTransactionServiceTest {

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
    @Mock
    private EmpChangeAuditService changeAuditService;

    private PendingTransactionService service;

    @BeforeEach
    void setUp() {
        service = new PendingTransactionService(pendingMapper, employeeMapper, stateMachine,
                employmentService, accountIntegration, headcountIntegration, changeAuditService);
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
    @DisplayName("幂等守卫：非 PENDING 事务不重复落账")
    class Idempotent {

        @Test
        @DisplayName("已 APPLIED 的事务 → 跳过，不改状态、不流转员工")
        void skipsAppliedTransaction() {
            PendingTransaction t = tx(PendingTransType.REGULAR, "APPLIED", "{}");
            when(pendingMapper.selectOne(any())).thenReturn(t);

            service.applySingle(1L, "TXN001");

            verify(pendingMapper, never()).updateById(any(PendingTransaction.class));
            verify(employeeMapper, never()).selectById(anyLong());
        }

        @Test
        @DisplayName("批次内全部为 PENDING → 逐条落账（幂等由查询侧 state=PENDING 保证）")
        void appliesEveryPendingInBatch() {
            PendingTransaction a = tx(PendingTransType.REGULAR, "PENDING", "{}");
            PendingTransaction b = tx(PendingTransType.RESIGN, "PENDING", "{}");
            when(pendingMapper.selectList(any())).thenReturn(List.of(a, b));
            when(employeeMapper.selectById(100L)).thenReturn(employee());

            assertEquals(2, service.applyDue(1L, LocalDate.now()));

            assertEquals("APPLIED", a.getState());
            assertEquals("APPLIED", b.getState());
        }

        @Test
        @DisplayName("非 PENDING 事务被 apply 直接跳过（并发翻转 state 的兜底防线）")
        void applyIsIdempotentForNonPending() {
            // applySingle 单笔入口不带 state 过滤，故幂等由 apply() 内部二次判定保障
            PendingTransaction applied = tx(PendingTransType.REGULAR, "APPLIED", "{}");
            when(pendingMapper.selectOne(any())).thenReturn(applied);

            service.applySingle(1L, "TXN001");

            verify(pendingMapper, never()).updateById(any(PendingTransaction.class));
            verify(employeeMapper, never()).selectById(anyLong());
        }
    }

    @Nested
    @DisplayName("未知事务类型守卫")
    class UnknownType {

        @Test
        @DisplayName("transType 无法识别 → EMP_ERROR，不静默吞掉（防脏数据丢落账）")
        void rejectsUnknownTransType() {
            PendingTransaction t = tx(null, "PENDING", "{}");
            t.setTransType("NOT_A_TYPE");
            when(pendingMapper.selectOne(any())).thenReturn(t);

            BizException e = assertThrows(BizException.class,
                    () -> service.applySingle(1L, "TXN001"));

            assertEquals(ErrorCode.EMP_ERROR.getCode(), e.getCode());
            verify(pendingMapper, never()).updateById(any(PendingTransaction.class));
        }
    }

    @Nested
    @DisplayName("单笔落账：存在性与状态置位")
    class ApplySingle {

        @Test
        @DisplayName("事务号不存在 → NOT_FOUND")
        void transactionNotFound() {
            when(pendingMapper.selectOne(any())).thenReturn(null);

            BizException e = assertThrows(BizException.class,
                    () -> service.applySingle(1L, "NOPE"));

            assertEquals(ErrorCode.NOT_FOUND.getCode(), e.getCode());
        }

        @Test
        @DisplayName("转正落账 → 触发状态机转在职，并把事务置为 APPLIED")
        void regularTransitionAndMarkApplied() {
            PendingTransaction t = tx(PendingTransType.REGULAR, "PENDING", "{}");
            when(pendingMapper.selectOne(any())).thenReturn(t);
            when(employeeMapper.selectById(100L)).thenReturn(employee());

            service.applySingle(1L, "TXN001");

            verify(stateMachine).transition(any(Employee.class),
                    eq(EmployeeStatus.ACTIVE),
                    anyString());
            assertEquals("APPLIED", t.getState());
            verify(pendingMapper).updateById(t);
        }
    }

    @Nested
    @DisplayName("离职落账：状态 + 雇佣关系 + 账号禁用三联动")
    class Resign {

        @Test
        @DisplayName("离职落账 → 置已离职、关闭雇佣关系、禁用账号")
        void resignAppliesAllThreeEffects() {
            PendingTransaction t = tx(PendingTransType.RESIGN, "PENDING",
                    "{\"resignType\":\"VOLUNTARY\"}");
            when(pendingMapper.selectOne(any())).thenReturn(t);
            when(employeeMapper.selectById(100L)).thenReturn(employee());

            service.applySingle(1L, "TXN001");

            verify(stateMachine).transition(any(Employee.class),
                    eq(EmployeeStatus.RESIGNED), anyString());
            verify(employmentService).closeCurrent(eq(100L), any());
            verify(accountIntegration).disable(1L, 100L);
            assertEquals("APPLIED", t.getState());
        }

        @Test
        @DisplayName("员工记录缺失 → NOT_FOUND，不落账（不产生半完成状态）")
        void resignEmployeeMissing() {
            PendingTransaction t = tx(PendingTransType.RESIGN, "PENDING", "{}");
            when(pendingMapper.selectOne(any())).thenReturn(t);
            when(employeeMapper.selectById(100L)).thenReturn(null);

            assertThrows(BizException.class, () -> service.applySingle(1L, "TXN001"));

            verify(pendingMapper, never()).updateById(any(PendingTransaction.class));
        }
    }

    @Nested
    @DisplayName("入职落账：状态 + 雇佣关系 + 建号三联动")
    class Onboard {

        @Test
        @DisplayName("入职落账 → 置试用、开雇佣关系、建账号")
        void onboardAppliesAllThreeEffects() {
            String payload = "{\"legalEntityId\":1,\"deptId\":2,\"positionId\":3,\"gradeId\":4,"
                    + "\"hireType\":\"FULL_TIME\",\"supervisorEmpId\":5,"
                    + "\"username\":\"E001\",\"password\":\"pwd123456\",\"roleCodes\":[\"HR\"]}";
            PendingTransaction t = tx(PendingTransType.ONBOARD, "PENDING", payload);
            when(pendingMapper.selectOne(any())).thenReturn(t);
            when(employeeMapper.selectById(100L)).thenReturn(employee());

            service.applySingle(1L, "TXN001");

            verify(stateMachine).transition(any(Employee.class),
                    eq(EmployeeStatus.PROBATION), anyString());
            verify(employmentService).open(eq(100L),
                    eq(1L), eq(2L),
                    eq(3L), eq(4L),
                    eq("FULL_TIME"),
                    eq(5L), any());
            verify(accountIntegration).createAccount(eq(1L),
                    eq(100L),
                    eq("E001"),
                    eq("pwd123456"), any());
        }

        @Test
        @DisplayName("payload 缺字段 → 传null 而非抛异常（不因载荷不全中断整批落账）")
        void onboardToleratesMissingPayloadFields() {
            PendingTransaction t = tx(PendingTransType.ONBOARD, "PENDING", "{}");
            when(pendingMapper.selectOne(any())).thenReturn(t);
            when(employeeMapper.selectById(100L)).thenReturn(employee());

            service.applySingle(1L, "TXN001");

            verify(employmentService).open(eq(100L),
                    isNull(), isNull(),
                    isNull(), isNull(),
                    isNull(), isNull(), any());
            assertEquals("APPLIED", t.getState());
        }
    }

    @Nested
    @DisplayName("applyDue：按批次落账并计数")
    class ApplyDue {

        @Test
        @DisplayName("全部落账成功 → 返回落账条数")
        void countsAppliedTransactions() {
            when(pendingMapper.selectList(any())).thenReturn(List.of(
                    tx(PendingTransType.REGULAR, "PENDING", "{}"),
                    tx(PendingTransType.REGULAR, "PENDING", "{}")));
            when(employeeMapper.selectById(100L)).thenReturn(employee());

            assertEquals(2, service.applyDue(1L, LocalDate.now()));
        }

        @Test
        @DisplayName("无到期事务 → 返回 0，不做任何状态变更")
        void returnsZeroWhenNothingDue() {
            when(pendingMapper.selectList(any())).thenReturn(List.of());

            assertEquals(0, service.applyDue(1L, LocalDate.now()));
            verify(employeeMapper, never()).selectById(anyLong());
        }
    }

    @Nested
    @DisplayName("编制占用回写（§10.5-B8）")
    class OccupancyWriteback {

        private Employment employment() {
            Employment em = new Employment();
            em.setEmpId(100L);
            em.setDeptId(2L);
            em.setPositionId(3L);
            em.setStatus(1);
            return em;
        }

        @Test
        @DisplayName("入职落账 → 回写 +1，幂等键=transNo 原值")
        void onboardWritesBackOccupy() {
            PendingTransaction t = tx(PendingTransType.ONBOARD, "PENDING",
                    "{\"deptId\":2,\"positionId\":3}");
            when(pendingMapper.selectOne(any())).thenReturn(t);
            when(employeeMapper.selectById(100L)).thenReturn(employee());

            service.applySingle(1L, "TXN001");

            ArgumentCaptor<com.hris.employee.dto.HcOccupancyRequest> captor =
                    ArgumentCaptor.forClass(com.hris.employee.dto.HcOccupancyRequest.class);
            verify(headcountIntegration).writeback(captor.capture());
            assertEquals(1, captor.getValue().delta());
            assertEquals("TXN001", captor.getValue().transNo());
            assertEquals(2L, captor.getValue().deptId());
        }

        @Test
        @DisplayName("离职落账 → 回写 -1（取 closeCurrent 前的现行雇佣关系）")
        void resignWritesBackRelease() {
            PendingTransaction t = tx(PendingTransType.RESIGN, "PENDING", "{}");
            when(pendingMapper.selectOne(any())).thenReturn(t);
            when(employeeMapper.selectById(100L)).thenReturn(employee());
            when(employmentService.current(100L)).thenReturn(employment());

            service.applySingle(1L, "TXN001");

            ArgumentCaptor<com.hris.employee.dto.HcOccupancyRequest> captor =
                    ArgumentCaptor.forClass(com.hris.employee.dto.HcOccupancyRequest.class);
            verify(headcountIntegration).writeback(captor.capture());
            assertEquals(-1, captor.getValue().delta());
            assertEquals("TXN001", captor.getValue().transNo());
        }

        @Test
        @DisplayName("调动落账 → 双腿回写旧-新+，幂等键带 :OUT/:IN 后缀")
        void transferWritesBackBothLegs() {
            PendingTransaction t = tx(PendingTransType.TRANSFER, "PENDING",
                    "{\"targetDeptId\":8,\"targetPositionId\":9}");
            when(pendingMapper.selectOne(any())).thenReturn(t);
            when(employeeMapper.selectById(100L)).thenReturn(employee());
            when(employmentService.current(100L)).thenReturn(employment());

            service.applySingle(1L, "TXN001");

            ArgumentCaptor<com.hris.employee.dto.HcOccupancyRequest> captor =
                    ArgumentCaptor.forClass(com.hris.employee.dto.HcOccupancyRequest.class);
            verify(headcountIntegration, times(2)).writeback(captor.capture());
            List<com.hris.employee.dto.HcOccupancyRequest> legs = captor.getAllValues();
            assertEquals(-1, legs.get(0).delta());
            assertEquals("TXN001:OUT", legs.get(0).transNo());
            assertEquals(1, legs.get(1).delta());
            assertEquals("TXN001:IN", legs.get(1).transNo());
        }

        @Test
        @DisplayName("雇佣关系缺失（dept/position 为空）→ 跳过回写，不阻断落账")
        void skipsWhenEmploymentMissing() {
            PendingTransaction t = tx(PendingTransType.ONBOARD, "PENDING", "{}");
            when(pendingMapper.selectOne(any())).thenReturn(t);
            when(employeeMapper.selectById(100L)).thenReturn(employee());

            service.applySingle(1L, "TXN001");

            verify(headcountIntegration, never()).writeback(any());
            assertEquals("APPLIED", t.getState());
        }
    }
}