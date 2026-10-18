package com.hris.employee.service;

import com.hris.employee.entity.PendingTransaction;
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
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * PendingTransactionService 的事务登记 API 单元测试。
 *
 * <p>与落账分发用例（PendingTransactionServiceTest）分文件的原因：本类只验"写入侧"契约
 * ——事务号生成、初始状态 PENDING、类型落 code、载荷序列化、待生效存在性；
 * 而落账分发要验证状态机/雇佣/账号三联动，arrange 差异大，混写会超 300 行门禁。</p>
 */
@ExtendWith(MockitoExtension.class)
class PendingTransactionLedgerTest {

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

    private PendingTransactionService service;

    @BeforeEach
    void setUp() {
        service = new PendingTransactionService(pendingMapper, employeeMapper, stateMachine,
                employmentService, accountIntegration);
    }

    @Nested
    @DisplayName("create：生成待生效事务")
    class Create {

        @Test
        @DisplayName("新建事务 → 状态 PENDING、类型落 code、载荷序列化")
        void createsPendingTransaction() {
            ArgumentCaptor<PendingTransaction> captor =
                    ArgumentCaptor.forClass(PendingTransaction.class);

            PendingTransaction created = service.create(100L, PendingTransType.TRANSFER,
                    LocalDate.now().plusDays(7), Map.of("targetDeptId", 9));

            verify(pendingMapper).insert(captor.capture());
            assertEquals("PENDING", captor.getValue().getState());
            assertEquals(PendingTransType.TRANSFER.getCode(), captor.getValue().getTransType());
            assertTrue(captor.getValue().getPayload().contains("targetDeptId"));
            assertEquals(created.getTransNo(), captor.getValue().getTransNo());
        }

        @Test
        @DisplayName("事务号带 txn 前缀，且两次创建不重复（幂等键前提）")
        void generatesDistinctTransNo() {
            PendingTransaction a = service.create(100L, PendingTransType.REGULAR,
                    LocalDate.now(), Map.of());
            PendingTransaction b = service.create(100L, PendingTransType.REGULAR,
                    LocalDate.now(), Map.of());

            assertTrue(a.getTransNo().startsWith("txn"), "事务号应带 txn 前缀");
            assertFalse(a.getTransNo().equals(b.getTransNo()), "事务号不应重复");
        }

        @Test
        @DisplayName("生效日期按入参落库（决定何时被调度落账）")
        void persistsEffectDate() {
            LocalDate effectDate = LocalDate.now().plusDays(30);

            PendingTransaction created = service.create(100L, PendingTransType.ONBOARD,
                    effectDate, Map.of());

            assertEquals(effectDate, created.getEffectDate());
        }
    }

    @Nested
    @DisplayName("hasPending：待生效事务存在性")
    class HasPending {

        @Test
        @DisplayName("存在 PENDING → true")
        void detectsPending() {
            when(pendingMapper.selectCount(any())).thenReturn(1L);
            assertTrue(service.hasPending(100L));
        }

        @Test
        @DisplayName("无 PENDING → false（count 为 null 也按 false）")
        void noPending() {
            when(pendingMapper.selectCount(any())).thenReturn(null);
            assertFalse(service.hasPending(100L));
        }

        @Test
        @DisplayName("count=0 → false（无待生效事务）")
        void zeroCountMeansNoPending() {
            when(pendingMapper.selectCount(any())).thenReturn(0L);
            assertFalse(service.hasPending(100L));
        }
    }
}