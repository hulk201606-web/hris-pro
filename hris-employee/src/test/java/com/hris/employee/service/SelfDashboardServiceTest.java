package com.hris.employee.service;

import com.hris.common.api.R;
import com.hris.employee.client.AttSelfClient;
import com.hris.employee.client.CmpSelfClient;
import com.hris.employee.client.FlowSelfClient;
import com.hris.employee.client.OrgSelfClient;
import com.hris.employee.dto.SelfDtos.SelfDashboardResponse;
import com.hris.employee.entity.Employee;
import com.hris.employee.entity.Employment;
import com.hris.employee.mapper.EmployeeMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

/**
 * 自助首页聚合（SELF-F-001）：全量成功 / 子项 fail-open。
 */
@ExtendWith(MockitoExtension.class)
class SelfDashboardServiceTest {

    @Mock
    private EmployeeMapper employeeMapper;
    @Mock
    private EmploymentService employmentService;
    @Mock
    private AttSelfClient attClient;
    @Mock
    private CmpSelfClient cmpClient;
    @Mock
    private FlowSelfClient flowClient;
    @Mock
    private OrgSelfClient orgClient;

    private SelfDashboardService service;

    @BeforeEach
    void setUp() {
        service = new SelfDashboardService(employeeMapper, employmentService, attClient,
                cmpClient, flowClient, orgClient);
    }

    private void stubLocal() {
        Employee e = new Employee();
        e.setId(100L);
        e.setName("张三");
        e.setEmpNo("E001");
        when(employeeMapper.selectById(100L)).thenReturn(e);
        Employment emp = new Employment();
        emp.setEmpId(100L);
        emp.setDeptId(5L);
        emp.setPositionId(6L);
        emp.setEffectiveFrom(LocalDate.of(2025, 3, 1));
        when(employmentService.current(100L)).thenReturn(emp);
        when(orgClient.depts(false)).thenReturn(R.ok(List.of(Map.of("id", 5, "name", "研发部"))));
        when(orgClient.positions()).thenReturn(R.ok(List.of(Map.of("id", 6, "name", "后端工程师"))));
    }

    @Test
    @DisplayName("五项全通：资料/考勤/假期/工资条/待办全部 live")
    void aggregatesAllSections() {
        stubLocal();
        when(attClient.daily(anyLong(), any(), any()))
                .thenReturn(R.ok(List.of(new AttSelfClient.DailyAttendance("NORMAL"))));
        when(attClient.attendanceBrief(anyList(), any(), any()))
                .thenReturn(R.ok(List.of(new AttSelfClient.AttendanceBrief(100L, 3, 0))));
        when(attClient.myQuotas(100L)).thenReturn(R.ok(new AttSelfClient.QuotaBalance(100L,
                List.of(new AttSelfClient.LeaveQuota(1L, "年假", new BigDecimal("5.5"))))));
        when(cmpClient.myPayslips()).thenReturn(R.ok(List.of(
                new CmpSelfClient.PayslipMe(1L, "2026-09", "5****", "2****"),
                new CmpSelfClient.PayslipMe(2L, "2026-10", "6****", "3****"))));
        when(flowClient.todo(1, 1)).thenReturn(R.ok(Map.of("total", 2)));

        SelfDashboardResponse resp = service.load(100L);

        assertEquals("研发部", resp.profile().deptName());
        assertEquals("后端工程师", resp.profile().positionName());
        assertEquals(LocalDate.of(2025, 3, 1), resp.profile().hireDate());
        assertEquals("NORMAL", resp.attendance().todayStatus());
        assertEquals(3, resp.attendance().monthLateCount());
        assertEquals("年假", resp.leaves().items().get(0).leaveType());
        assertEquals("5.5", resp.leaves().items().get(0).remainingDays());
        assertEquals("2026-10", resp.payslip().latestPeriod());
        assertEquals("3****", resp.payslip().netAmountMasked());
        assertEquals(2, resp.pendingFlows().count());
        assertFalse(resp.partial());
    }

    @Test
    @DisplayName("工资条无权/异常 → payslip 置 null + fallback，其余照常")
    void payslipFailsOpen() {
        stubLocal();
        when(attClient.daily(anyLong(), any(), any()))
                .thenReturn(R.ok(List.of(new AttSelfClient.DailyAttendance("NORMAL"))));
        when(attClient.attendanceBrief(anyList(), any(), any()))
                .thenReturn(R.ok(List.of(new AttSelfClient.AttendanceBrief(100L, 0, 0))));
        when(attClient.myQuotas(100L)).thenReturn(R.ok(new AttSelfClient.QuotaBalance(100L, List.of())));
        when(cmpClient.myPayslips()).thenThrow(new RuntimeException("cmp forbidden"));
        when(flowClient.todo(1, 1)).thenReturn(R.ok(Map.of("total", 0)));

        SelfDashboardResponse resp = service.load(100L);

        assertNull(resp.payslip().latestPeriod());
        assertEquals("fallback", resp.payslip().source());
        assertEquals("NORMAL", resp.attendance().todayStatus());
        assertTrue(resp.partial());
    }

    @Test
    @DisplayName("组织字典失败 → 部门/岗位名称 null + profile fallback，不影响考勤")
    void orgFailureDegradesProfile() {
        Employee e = new Employee();
        e.setId(100L);
        e.setName("张三");
        e.setEmpNo("E001");
        when(employeeMapper.selectById(100L)).thenReturn(e);
        Employment emp = new Employment();
        emp.setEmpId(100L);
        emp.setDeptId(5L);
        emp.setPositionId(6L);
        when(employmentService.current(100L)).thenReturn(emp);
        when(orgClient.depts(false)).thenThrow(new RuntimeException("org down"));
        when(attClient.daily(anyLong(), any(), any())).thenReturn(R.ok(List.of()));
        when(attClient.attendanceBrief(anyList(), any(), any())).thenReturn(R.ok(List.of()));
        when(attClient.myQuotas(100L)).thenReturn(R.ok(new AttSelfClient.QuotaBalance(100L, List.of())));
        when(cmpClient.myPayslips()).thenReturn(R.ok(List.of()));
        when(flowClient.todo(1, 1)).thenReturn(R.ok(Map.of("total", 1)));

        SelfDashboardResponse resp = service.load(100L);

        assertNull(resp.profile().deptName());
        assertEquals("fallback", resp.profile().source());
        assertEquals("NO_RECORD", resp.attendance().todayStatus());
        assertEquals(1, resp.pendingFlows().count());
    }
}
