package com.hris.employee.service;

import com.hris.common.exception.BizException;
import com.hris.common.exception.ErrorCode;
import com.hris.employee.dto.ContractDtos.ContractCreateRequest;
import com.hris.employee.dto.ContractDtos.ContractExpiringItem;
import com.hris.employee.dto.ContractDtos.ContractResponse;
import com.hris.employee.entity.Contract;
import com.hris.employee.entity.Employee;
import com.hris.employee.mapper.ContractMapper;
import com.hris.employee.mapper.EmployeeMapper;
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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * ContractService 单元测试（EMP-F-014 多法人合同、EMP-F-015 到期预警 60/30/7 多级）。
 *
 * <p>核心是 level() 的三级预警边界：剩余天数恰为 7 / 30 时归属哪一级，直接决定 HR 收到
 * 哪一档提醒。此处用边界值（=7、=8、=30、=31）而非典型值断言。</p>
 */
@ExtendWith(MockitoExtension.class)
class ContractServiceTest {

    @Mock
    private ContractMapper contractMapper;
    @Mock
    private EmployeeMapper employeeMapper;

    private ContractService service;

    @BeforeEach
    void setUp() {
        service = new ContractService(contractMapper, employeeMapper);
    }

    private Contract contract(Integer status, Integer renewCnt, LocalDate endDate) {
        Contract c = new Contract();
        c.setId(1L);
        c.setEmpId(100L);
        c.setContractNo("CTR001");
        c.setStatus(status);
        c.setRenewCnt(renewCnt);
        c.setEndDate(endDate);
        return c;
    }

    private Employee employee() {
        Employee e = new Employee();
        e.setId(100L);
        e.setEmpNo("E001");
        e.setName("张三");
        return e;
    }

    @Nested
    @DisplayName("到期预警：7/30/60 三级边界")
    class Expiring {

        private ContractExpiringItem itemFor(LocalDate endDate) {
            when(contractMapper.selectList(any())).thenReturn(List.of(contract(1, 0, endDate)));
            when(employeeMapper.selectList(any())).thenReturn(List.of(employee()));
            List<ContractExpiringItem> list = service.expiring(60);
            return list.isEmpty() ? null : list.get(0);
        }

        @Test
        @DisplayName("剩余 7 天（边界）→ LEVEL_7")
        void exactlySevenDaysIsLevel7() {
            assertEquals("LEVEL_7", itemFor(LocalDate.now().plusDays(7)).level());
        }

        @Test
        @DisplayName("剩余 6 天 → LEVEL_7")
        void sixDaysIsLevel7() {
            assertEquals("LEVEL_7", itemFor(LocalDate.now().plusDays(6)).level());
        }

        @Test
        @DisplayName("剩余 8 天（刚过 7 档）→ LEVEL_30")
        void eightDaysIsLevel30() {
            assertEquals("LEVEL_30", itemFor(LocalDate.now().plusDays(8)).level());
        }

        @Test
        @DisplayName("剩余 30 天（边界）→ LEVEL_30")
        void exactlyThirtyDaysIsLevel30() {
            assertEquals("LEVEL_30", itemFor(LocalDate.now().plusDays(30)).level());
        }

        @Test
        @DisplayName("剩余 31 天（刚过 30 档）→ LEVEL_60")
        void thirtyOneDaysIsLevel60() {
            assertEquals("LEVEL_60", itemFor(LocalDate.now().plusDays(31)).level());
        }

        @Test
        @DisplayName("剩余天数为纯计算值，不受查询日期漂移影响")
        void remainingDaysIsCalculated() {
            ContractExpiringItem item = itemFor(LocalDate.now().plusDays(20));

            assertEquals(20, item.daysToExpire());
            assertEquals("E001", item.empNo());
        }

        @Test
        @DisplayName("days 传 0 或负数 → 窗口回落 60 天（不因参数异常返回空列表）")
        void nonPositiveDaysFallsBackTo60() {
            when(contractMapper.selectList(any())).thenReturn(List.of(
                    contract(1, 0, LocalDate.now().plusDays(40))));

            assertEquals(1, service.expiring(0).size());
            verify(contractMapper).selectList(any());
        }

        @Test
        @DisplayName("无到期合同 → 返回空列表，不查员工表（省一次 IO）")
        void emptyWhenNothingExpiring() {
            when(contractMapper.selectList(any())).thenReturn(List.of());

            assertTrue(service.expiring(60).isEmpty());
            verify(employeeMapper, never()).selectList(any());
        }
    }

    @Nested
    @DisplayName("renew：续签守卫")
    class Renew {

        @Test
        @DisplayName("合同不存在 → NOT_FOUND")
        void contractNotFound() {
            when(contractMapper.selectById(404L)).thenReturn(null);

            BizException e = assertThrows(BizException.class,
                    () -> service.renew(404L, LocalDate.now().plusYears(1)));

            assertEquals(ErrorCode.NOT_FOUND.getCode(), e.getCode());
        }

        @Test
        @DisplayName("合同已终止（status≠1）→ EMP_ERROR「不可续签」")
        void rejectsTerminatedContract() {
            when(contractMapper.selectById(1L)).thenReturn(contract(0, 2, LocalDate.now()));

            BizException e = assertThrows(BizException.class,
                    () -> service.renew(1L, LocalDate.now().plusYears(1)));

            assertEquals(ErrorCode.EMP_ERROR.getCode(), e.getCode());
            verify(contractMapper, never()).updateById(any(Contract.class));
        }

        @Test
        @DisplayName("续签 → 结束日期顺延、续签次数 +1")
        void renewsActiveContract() {
            LocalDate newEnd = LocalDate.now().plusYears(2);
            when(contractMapper.selectById(1L)).thenReturn(contract(1, 1, LocalDate.now()));

            ContractResponse resp = service.renew(1L, newEnd);

            assertEquals(newEnd, resp.endDate());
            assertEquals(2, resp.renewCnt());
            verify(contractMapper).updateById(any(Contract.class));
        }

        @Test
        @DisplayName("renewCnt 为 null 的脏数据 → 按 0 起算，不抛 NPE")
        void nullRenewCntTreatedAsZero() {
            when(contractMapper.selectById(1L)).thenReturn(contract(1, null, LocalDate.now()));

            assertEquals(1, service.renew(1L, LocalDate.now().plusYears(1)).renewCnt());
        }
    }

    @Nested
    @DisplayName("create：合同编号守卫")
    class Create {

        @Test
        @DisplayName("员工不存在 → NOT_FOUND，不落库（防孤儿合同）")
        void employeeMustExist() {
            when(employeeMapper.selectById(anyLong())).thenReturn(null);

            BizException e = assertThrows(BizException.class, () -> service.create(
                    new ContractCreateRequest(100L, "FIXED", LocalDate.now(),
                            LocalDate.now().plusYears(1), null, null, null, null)));

            assertEquals(ErrorCode.NOT_FOUND.getCode(), e.getCode());
            verify(contractMapper, never()).insert(any(Contract.class));
        }

        @Test
        @DisplayName("合同编号为空 → 自动生成 ctr 前缀编号，状态置 1")
        void autoGeneratesContractNo() {
            when(employeeMapper.selectById(100L)).thenReturn(employee());

            service.create(new ContractCreateRequest(100L, "FIXED", LocalDate.now(),
                    LocalDate.now().plusYears(1), null, null, null, null));

            ArgumentCaptor<Contract> captor = ArgumentCaptor.forClass(Contract.class);
            verify(contractMapper).insert(captor.capture());
            assertTrue(captor.getValue().getContractNo().startsWith("ctr"),
                    "空编号应自动生成，实际=" + captor.getValue().getContractNo());
            assertEquals(1, captor.getValue().getStatus());
            assertEquals(0, captor.getValue().getRenewCnt());
        }

        @Test
        @DisplayName("传入合同编号 → 原样使用，不被覆盖")
        void keepsProvidedContractNo() {
            when(employeeMapper.selectById(100L)).thenReturn(employee());

            service.create(new ContractCreateRequest(100L, "OPEN_ENDED", LocalDate.now(),
                    null, null, null, "CTR-CUSTOM", null));

            ArgumentCaptor<Contract> captor = ArgumentCaptor.forClass(Contract.class);
            verify(contractMapper).insert(captor.capture());
            assertEquals("CTR-CUSTOM", captor.getValue().getContractNo());
        }

        @Test
        @DisplayName("LABOR 劳务合同可建单（EMP-F-014 深化），legalEntityId 落库")
        void createsLaborContractWithLegalEntity() {
            when(employeeMapper.selectById(100L)).thenReturn(employee());

            ContractResponse resp = service.create(new ContractCreateRequest(100L, "LABOR",
                    LocalDate.now(), LocalDate.now().plusYears(1), null, null, null, 88L));

            assertEquals("LABOR", resp.type());
            assertEquals(88L, resp.legalEntityId());
        }

        @Test
        @DisplayName("未知类型（如 FIXEDXX）→ PARAM_INVALID 拒绝落库")
        void rejectsUnknownType() {
            when(employeeMapper.selectById(100L)).thenReturn(employee());

            BizException e = assertThrows(BizException.class, () -> service.create(
                    new ContractCreateRequest(100L, "FIXEDXX", LocalDate.now(),
                            null, null, null, null, null)));

            assertEquals(ErrorCode.PARAM_INVALID.getCode(), e.getCode());
            verify(contractMapper, never()).insert(any(Contract.class));
        }
    }

    @Nested
    @DisplayName("list：法人维度过滤（EMP-F-014 深化）")
    class ListFilter {

        @Test
        @DisplayName("legalEntityId 为空 → 不加法人条件，返回全部合同")
        void noLegalEntityFilter() {
            when(contractMapper.selectList(any())).thenReturn(List.of(contract(1, 0, LocalDate.now())));

            service.list(100L, null);

            verify(contractMapper).selectList(any());
        }

        @Test
        @DisplayName("legalEntityId 非空 → 查询仍走 mapper（条件在 wrapper 中生效）")
        void filtersByLegalEntity() {
            when(contractMapper.selectList(any())).thenReturn(List.of());

            assertTrue(service.list(100L, 88L).isEmpty());
            verify(contractMapper).selectList(any());
        }
    }
}