package com.hris.employee.service;

import com.hris.common.exception.BizException;
import com.hris.common.exception.ErrorCode;
import com.hris.employee.entity.Employee;
import com.hris.employee.entity.StatusLog;
import com.hris.employee.enums.EmployeeStatus;
import com.hris.employee.mapper.EmployeeMapper;
import com.hris.employee.mapper.StatusLogMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * EmployeeStateMachine 单元测试（EMP-F-018：所有状态流转统一经此校验）。
 *
 * <p>三道守卫：①当前状态非法（脏数据）②流转不合法③按 id 流转时员工不存在。
 * 另锁一条数据完整性要求：合法流转必须落状态流水（docs/03-02 §2，终态尤其要求），
 * 否则状态变更不可追溯。</p>
 */
@ExtendWith(MockitoExtension.class)
class EmployeeStateMachineTest {

    @Mock
    private EmployeeMapper employeeMapper;
    @Mock
    private StatusLogMapper statusLogMapper;

    private EmployeeStateMachine stateMachine;

    @BeforeEach
    void setUp() {
        stateMachine = new EmployeeStateMachine(employeeMapper, statusLogMapper);
    }

    private Employee emp(Integer status) {
        Employee e = new Employee();
        e.setId(100L);
        e.setEmpNo("E001");
        e.setStatus(status);
        return e;
    }

    @Nested
    @DisplayName("守卫 ①：当前状态非法")
    class IllegalCurrent {

        @Test
        @DisplayName("员工状态 code 为 null → EMP_ERROR「员工状态非法」，不更新不落流水")
        void nullStatusRejected() {
            Employee e = emp(null);

            BizException ex = assertThrows(BizException.class,
                    () -> stateMachine.transition(e, EmployeeStatus.ACTIVE, "转正"));

            assertEquals(ErrorCode.EMP_ERROR.getCode(), ex.getCode());
            verify(employeeMapper, never()).updateById(any(Employee.class));
            verify(statusLogMapper, never()).insert(any(StatusLog.class));
        }

        @Test
        @DisplayName("员工状态 code 越界（如 99）→ EMP_ERROR，不更新不落流水")
        void unknownStatusRejected() {
            Employee e = emp(99);

            assertThrows(BizException.class,
                    () -> stateMachine.transition(e, EmployeeStatus.ACTIVE, "转正"));

            verify(employeeMapper, never()).updateById(any(Employee.class));
            verify(statusLogMapper, never()).insert(any(StatusLog.class));
        }
    }

    @Nested
    @DisplayName("守卫 ②：流转不合法")
    class IllegalTransition {

        @Test
        @DisplayName("待入职直接转在职 → EMP_ERROR，且不落库不落流水")
        void pendingToActiveRejected() {
            Employee e = emp(EmployeeStatus.PENDING.getCode());

            BizException ex = assertThrows(BizException.class,
                    () -> stateMachine.transition(e, EmployeeStatus.ACTIVE, "跳过试用"));

            assertEquals(ErrorCode.EMP_ERROR.getCode(), ex.getCode());
            verify(employeeMapper, never()).updateById(any(Employee.class));
            verify(statusLogMapper, never()).insert(any(StatusLog.class));
        }

        @Test
        @DisplayName("异常信息含中文状态标签，便于定位")
        void messageContainsLabels() {
            Employee e = emp(EmployeeStatus.PENDING.getCode());

            BizException ex = assertThrows(BizException.class,
                    () -> stateMachine.transition(e, EmployeeStatus.ACTIVE, "非法"));

            assertTrue(ex.getMessage().contains(EmployeeStatus.PENDING.getLabel())
                    && ex.getMessage().contains(EmployeeStatus.ACTIVE.getLabel()),
                    "异常信息应含起止状态标签，实际=" + ex.getMessage());
        }
    }

    @Nested
    @DisplayName("合法流转：更新状态并落流水")
    class Legal {

        @Test
        @DisplayName("试用 → 在职：状态落库为新 code")
        void updatesStatusOnLegalTransition() {
            Employee e = emp(EmployeeStatus.PROBATION.getCode());

            stateMachine.transition(e, EmployeeStatus.ACTIVE, "转正生效");

            assertEquals(EmployeeStatus.ACTIVE.getCode(), e.getStatus());
            verify(employeeMapper).updateById(e);
        }

        @Test
        @DisplayName("试用 → 在职：落流水含 from/to/reason 三元组（可追溯）")
        void writesStatusLogWithFromToReason() {
            Employee e = emp(EmployeeStatus.PROBATION.getCode());

            stateMachine.transition(e, EmployeeStatus.ACTIVE, "转正生效");

            ArgumentCaptor<StatusLog> captor = ArgumentCaptor.forClass(StatusLog.class);
            verify(statusLogMapper).insert(captor.capture());
            assertEquals(100L, captor.getValue().getEmpId());
            assertEquals(EmployeeStatus.PROBATION.getCode(), captor.getValue().getFromStatus());
            assertEquals(EmployeeStatus.ACTIVE.getCode(), captor.getValue().getToStatus());
            assertEquals("转正生效", captor.getValue().getReason());
        }

        @Test
        @DisplayName("在职 → 离职中（终态前的末段）→ 同样落流水，流转链可完整追溯")
        void resigningTransitionAlsoLogged() {
            Employee e = emp(EmployeeStatus.ACTIVE.getCode());

            stateMachine.transition(e, EmployeeStatus.RESIGNING, "离职流程通过");

            ArgumentCaptor<StatusLog> captor = ArgumentCaptor.forClass(StatusLog.class);
            verify(statusLogMapper).insert(captor.capture());
            assertEquals(EmployeeStatus.RESIGNING.getCode(), captor.getValue().getToStatus());
            assertEquals("离职流程通过", captor.getValue().getReason());
        }

        @Test
        @DisplayName("在职 → 已离职直达被拦截：须经离职中，体现两段式离任约束")
        void activeToResignedDirectlyRejected() {
            Employee e = emp(EmployeeStatus.ACTIVE.getCode());

            assertThrows(BizException.class,
                    () -> stateMachine.transition(e, EmployeeStatus.RESIGNED, "直接离职"));

            verify(statusLogMapper, never()).insert(any(StatusLog.class));
        }
    }

    @Nested
    @DisplayName("按 id 流转的重载")
    class ById {

        @Test
        @DisplayName("员工不存在 → NOT_FOUND，不落流水")
        void employeeNotFound() {
            when(employeeMapper.selectById(404L)).thenReturn(null);

            BizException ex = assertThrows(BizException.class,
                    () -> stateMachine.transition(404L, EmployeeStatus.ACTIVE, "转正"));

            assertEquals(ErrorCode.NOT_FOUND.getCode(), ex.getCode());
            verify(statusLogMapper, never()).insert(any(StatusLog.class));
        }

        @Test
        @DisplayName("员工存在 → 重新加载后正常流转并落流水")
        void reloadsAndTransitions() {
            when(employeeMapper.selectById(100L)).thenReturn(emp(EmployeeStatus.PROBATION.getCode()));

            stateMachine.transition(100L, EmployeeStatus.ACTIVE, "转正生效");

            verify(employeeMapper).selectById(100L);
            verify(employeeMapper).updateById(any(Employee.class));
            verify(statusLogMapper).insert(any(StatusLog.class));
        }
    }
}