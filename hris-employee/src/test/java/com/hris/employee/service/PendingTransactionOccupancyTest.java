package com.hris.employee.service;

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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * PendingTransactionService 的编制占用回写单测（§10.5-B8）。
 *
 * <p>从 PendingTransactionServiceTest 拆分而来（单文件 ≤300 行门禁），夹具与原类一致。
 * 入职 +1 / 离职 -1 / 调动双腿（:OUT -1、:IN +1），幂等键取 transNo 原值。</p>
 */
@ExtendWith(MockitoExtension.class)
class PendingTransactionOccupancyTest {

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
