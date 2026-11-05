package com.hris.employee.service;

import com.hris.common.exception.BizException;
import com.hris.common.exception.ErrorCode;
import com.hris.employee.dto.SelfDtos.CriticalChangeRequest;
import com.hris.employee.dto.SelfDtos.SelfProfileUpdateRequest;
import com.hris.employee.entity.EmpProfileChange;
import com.hris.employee.entity.Employee;
import com.hris.employee.entity.Employment;
import com.hris.employee.integration.FlowIntegration;
import com.hris.employee.mapper.EmpProfileChangeMapper;
import com.hris.employee.mapper.EmployeeMapper;
import com.hris.employee.mapper.ProfileEmergencyMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
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
 * 自助信息变更（SELF-F-002）：非关键直改 / 关键拒绝 / 敏感列路径 / 审批回调。
 */
@ExtendWith(MockitoExtension.class)
class SelfProfileServiceTest {

    @Mock
    private EmployeeMapper employeeMapper;
    @Mock
    private ProfileEmergencyMapper emergencyMapper;
    @Mock
    private EmpProfileChangeMapper changeRequestMapper;
    @Mock
    private EmpChangeAuditService auditService;
    @Mock
    private EmployeeService employeeService;
    @Mock
    private EmploymentService employmentService;
    @Mock
    private FlowIntegration flowIntegration;

    private SelfProfileService service;

    @BeforeEach
    void setUp() {
        service = new SelfProfileService(employeeMapper, emergencyMapper, changeRequestMapper,
                auditService, employeeService, employmentService, flowIntegration);
    }

    private Employee employee() {
        Employee e = new Employee();
        e.setId(100L);
        e.setEmpNo("E001");
        e.setName("张三");
        e.setEmail("old@x.com");
        e.setMobileCipher("13800138000");
        e.setMobileMask("138****8000");
        return e;
    }

    private void stubProfileRead(Employee e) {
        when(employeeMapper.selectById(100L)).thenReturn(e);
        Employment emp = new Employment();
        emp.setEmpId(100L);
        emp.setDeptId(5L);
        emp.setPositionId(6L);
        emp.setHireType("FULLTIME");
        when(employmentService.current(100L)).thenReturn(emp);
        when(emergencyMapper.selectList(any())).thenReturn(List.of());
    }

    @Test
    @DisplayName("非关键字段直改生效：姓名/邮箱/地址写入并落 diff 审计")
    void updatesNonCriticalFields() {
        Employee e = employee();
        stubProfileRead(e);

        service.update(100L, new SelfProfileUpdateRequest("李四", null, "new@x.com",
                "上海市", "王五", "13700137000", null, null, null, null));

        ArgumentCaptor<Employee> captor = ArgumentCaptor.forClass(Employee.class);
        verify(employeeMapper).updateById(captor.capture());
        assertEquals("李四", captor.getValue().getName());
        assertEquals("new@x.com", captor.getValue().getEmail());
        assertEquals("上海市", captor.getValue().getAddress());
        verify(auditService).record(eq(100L), eq("SELF_UPDATE"), any(), any(), eq("自助信息变更"));
    }

    @Test
    @DisplayName("关键字段（证件号）出现 → PARAM_INVALID，文案含 EMP_PROFILE_CHG，不写库")
    void rejectsCriticalField() {
        BizException ex = assertThrows(BizException.class, () -> service.update(100L,
                new SelfProfileUpdateRequest(null, null, null, null, null, null,
                        "110101199001011234", null, null, null)));

        assertEquals(ErrorCode.PARAM_INVALID.getCode(), ex.getCode());
        assertTrue(ex.getMessage().contains("EMP_PROFILE_CHG"), "实际=" + ex.getMessage());
        verify(employeeMapper, never()).updateById(any(Employee.class));
    }

    @Test
    @DisplayName("关键字段（银行卡/雇佣类型）同样拒绝")
    void rejectsBankAndHireType() {
        assertThrows(BizException.class, () -> service.update(100L,
                new SelfProfileUpdateRequest(null, null, null, null, null, null,
                        null, null, "6222000011112222", null)));
        assertThrows(BizException.class, () -> service.update(100L,
                new SelfProfileUpdateRequest(null, null, null, null, null, null,
                        null, null, null, "INTERN")));
    }

    @Test
    @DisplayName("手机号走实体密文列 + 刷新掩码（不绕过 TypeHandler 路径）")
    void mobileUsesCipherColumn() {
        Employee e = employee();
        stubProfileRead(e);

        service.update(100L, new SelfProfileUpdateRequest(null, "13900139000",
                null, null, null, null, null, null, null, null));

        ArgumentCaptor<Employee> captor = ArgumentCaptor.forClass(Employee.class);
        verify(employeeMapper).updateById(captor.capture());
        assertEquals("13900139000", captor.getValue().getMobileCipher(),
                "明文语义写入密文列，由 TypeHandler 在 JDBC 边界加密");
        assertEquals("139****9000", captor.getValue().getMobileMask());
    }

    @Test
    @DisplayName("发起关键信息变更：落申请单并发起 EMP_PROFILE_CHG 流程")
    void startsProfileChangeFlow() {
        when(employeeMapper.selectById(100L)).thenReturn(employee());
        when(flowIntegration.start(eq("EMP_PROFILE_CHG"), anyString(), any(), any(), any()))
                .thenReturn("pi-1");

        var resp = service.requestChange(100L,
                new CriticalChangeRequest("110101199001011234", null, null, null, "换证"));

        assertEquals("EMP_PROFILE_CHG", resp.templateKey());
        assertEquals("PENDING", resp.status());
        assertEquals("pi-1", resp.procInstId());
        ArgumentCaptor<EmpProfileChange> row = ArgumentCaptor.forClass(EmpProfileChange.class);
        verify(changeRequestMapper).insert(row.capture());
        assertEquals("PENDING", row.getValue().getStatus());
        assertTrue(row.getValue().getChangesJson().contains("certNo"));
    }

    @Test
    @DisplayName("审批通过回调：按 bizKey 落账并置 APPLIED")
    void appliesApprovedChange() {
        EmpProfileChange row = new EmpProfileChange();
        row.setId(1L);
        row.setEmpId(100L);
        row.setBizKey("pcx");
        row.setChangesJson("{\"certNo\":\"110101199001011234\"}");
        row.setStatus("PENDING");
        when(changeRequestMapper.selectOne(any())).thenReturn(row);

        var result = service.handleCallback("pcx", true, "同意");

        assertEquals(SelfProfileService.CallbackResult.APPLIED, result);
        verify(employeeService).applyProfileChange(eq(100L), any(Map.class), eq("同意"));
        assertEquals("APPLIED", row.getStatus());
    }

    @Test
    @DisplayName("非本类单据 → NOT_FOUND，交还生命周期单处理")
    void notFoundForOtherBizKey() {
        when(changeRequestMapper.selectOne(any())).thenReturn(null);

        assertEquals(SelfProfileService.CallbackResult.NOT_FOUND,
                service.handleCallback("other", true, null));
        verify(employeeService, never()).applyProfileChange(anyLong(), any(), anyString());
    }
}
