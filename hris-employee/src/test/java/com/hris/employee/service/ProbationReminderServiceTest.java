package com.hris.employee.service;

import com.hris.employee.dto.EmployeeDtos.ProbationReminderItem;
import com.hris.employee.entity.Contract;
import com.hris.employee.entity.Employee;
import com.hris.employee.entity.Employment;
import com.hris.employee.mapper.ContractMapper;
import com.hris.employee.mapper.EmployeeMapper;
import com.hris.employee.mapper.EmploymentMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * ProbationReminderService 单元测试（EMP-F-007 深化：15/7/3 天三档提醒）。
 *
 * <p>窗口计算与档位归档直接决定 HR 收到哪一档提醒：3/7 天是档位边界，
 * 窗口首尾（今天 / 今天+window）都必须命中。</p>
 */
@ExtendWith(MockitoExtension.class)
class ProbationReminderServiceTest {

    @Mock
    private ContractMapper contractMapper;
    @Mock
    private EmployeeMapper employeeMapper;
    @Mock
    private EmploymentMapper employmentMapper;

    private ProbationReminderService service;

    @BeforeEach
    void setUp() {
        service = new ProbationReminderService(contractMapper, employeeMapper, employmentMapper);
    }

    private Contract contract(Long id, LocalDate probationEnd) {
        Contract c = new Contract();
        c.setId(id);
        c.setEmpId(100L);
        c.setContractNo("CTR001");
        c.setStatus(1);
        c.setProbationEnd(probationEnd);
        return c;
    }

    private Employee employee() {
        Employee e = new Employee();
        e.setId(100L);
        e.setEmpNo("E001");
        e.setName("张三");
        return e;
    }

    private Employment employment() {
        Employment em = new Employment();
        em.setEmpId(100L);
        em.setDeptId(7L);
        em.setStatus(1);
        return em;
    }

    private ProbationReminderItem itemFor(LocalDate probationEnd) {
        when(contractMapper.selectList(any())).thenReturn(List.of(contract(1L, probationEnd)));
        when(employeeMapper.selectList(any())).thenReturn(List.of(employee()));
        when(employmentMapper.selectList(any())).thenReturn(List.of(employment()));
        List<ProbationReminderItem> list = service.reminders(15);
        return list.isEmpty() ? null : list.get(0);
    }

    @Nested
    @DisplayName("档位归档：15/7/3 三档边界")
    class Leveling {

        @Test
        @DisplayName("剩余 3 天（边界）→ D3")
        void threeDaysIsD3() {
            assertEquals("D3", ProbationReminderService.level(3));
        }

        @Test
        @DisplayName("剩余 4 天 → D7")
        void fourDaysIsD7() {
            assertEquals("D7", ProbationReminderService.level(4));
        }

        @Test
        @DisplayName("剩余 7 天（边界）→ D7")
        void sevenDaysIsD7() {
            assertEquals("D7", ProbationReminderService.level(7));
        }

        @Test
        @DisplayName("剩余 8 天 → D15")
        void eightDaysIsD15() {
            assertEquals("D15", ProbationReminderService.level(8));
        }

        @Test
        @DisplayName("剩余 15 天（窗口边界）→ D15")
        void fifteenDaysIsD15() {
            assertEquals("D15", ProbationReminderService.level(15));
        }
    }

    @Nested
    @DisplayName("窗口查询与摘要组装")
    class Window {

        @Test
        @DisplayName("窗口内到期 → 返回姓名/工号/部门/剩余天数摘要")
        void buildsSummary() {
            ProbationReminderItem item = itemFor(LocalDate.now().plusDays(10));

            assertEquals("E001", item.empNo());
            assertEquals("张三", item.name());
            assertEquals(7L, item.deptId());
            assertEquals(10, item.daysRemaining());
            assertEquals("D15", item.level());
        }

        @Test
        @DisplayName("窗口首日（今天到期）→ 命中，剩余 0 天，D3 档")
        void todayIsIncluded() {
            ProbationReminderItem item = itemFor(LocalDate.now());

            assertEquals(0, item.daysRemaining());
            assertEquals("D3", item.level());
        }

        @Test
        @DisplayName("窗口末日（今天+15）→ 命中 D15 档")
        void windowEndIsIncluded() {
            ProbationReminderItem item = itemFor(LocalDate.now().plusDays(15));

            assertEquals("D15", item.level());
        }

        @Test
        @DisplayName("无到期合同 → 空列表，不查员工表")
        void emptyWhenNothingInWindow() {
            when(contractMapper.selectList(any())).thenReturn(List.of());

            assertTrue(service.reminders(15).isEmpty());
            org.mockito.Mockito.verify(employeeMapper, org.mockito.Mockito.never()).selectList(any());
        }

        @Test
        @DisplayName("windowDays 传 0 → 回落默认 15 天窗口（不因参数异常漏提醒）")
        void nonPositiveWindowFallsBack() {
            when(contractMapper.selectList(any())).thenReturn(List.of(contract(1L, LocalDate.now().plusDays(10))));
            when(employeeMapper.selectList(any())).thenReturn(List.of(employee()));
            when(employmentMapper.selectList(any())).thenReturn(List.of(employment()));

            assertEquals(1, service.reminders(0).size());
        }
    }
}
