package com.hris.employee.service;

import com.hris.employee.dto.ProfileDtos.ProfileEducationDto;
import com.hris.employee.dto.ProfileDtos.ProfileWorkDto;
import com.hris.employee.dto.EmployeeDtos.EmployeeProfileResponse;
import com.hris.employee.entity.Employee;
import com.hris.employee.entity.Employment;
import com.hris.employee.mapper.ChangeAuditMapper;
import com.hris.employee.mapper.EmployeeMapper;
import com.hris.employee.mapper.EmploymentMapper;
import com.hris.common.security.AuthContextHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * EMP-F-001 字段组权限 + 分签独立维护 单元测试。
 *
 * <p>读取侧按权限码过滤敏感分组：无 emp:profile:read-* 权限码（或上下文缺失）时
 * 该分组必须返回空集合——脱敏兜底，宁可不发不可多发。</p>
 */
@ExtendWith(MockitoExtension.class)
class EmployeeFieldGroupTest {

    @Mock
    private EmployeeMapper employeeMapper;
    @Mock
    private EmploymentMapper employmentMapper;
    @Mock
    private ChangeAuditMapper changeAuditMapper;
    @Mock
    private EmpNoService empNoService;
    @Mock
    private EmploymentService employmentService;
    @Mock
    private ProfileService profileService;
    @Mock
    private EmployeeStateMachine stateMachine;

    private EmployeeService service;

    @BeforeEach
    void setUp() {
        service = new EmployeeService(employeeMapper, employmentMapper, changeAuditMapper,
                empNoService, employmentService, profileService, stateMachine);
    }

    @AfterEach
    void tearDown() {
        AuthContextHolder.clear();
    }

    private Employee employee() {
        Employee e = new Employee();
        e.setId(100L);
        e.setEmpNo("E001");
        e.setName("张三");
        e.setStatus(2);
        return e;
    }

    private ProfileService.ProfileSnapshot snapshot() {
        return new ProfileService.ProfileSnapshot(
                List.of(new ProfileEducationDto(1L, "清华大学", "计算机", "本科", null, null)),
                List.of(new ProfileWorkDto(1L, "某公司", "工程师", null, null, null)),
                List.of(), List.of());
    }

    private void stubProfile() {
        when(employeeMapper.selectById(100L)).thenReturn(employee());
        when(employmentService.current(100L)).thenReturn(null);
        when(profileService.load(100L)).thenReturn(snapshot());
    }

    @Nested
    @DisplayName("字段组权限过滤（读取侧）")
    class FieldPermFilter {

        @Test
        @DisplayName("四组权限码齐全 → 各分组原样返回")
        void allGroupsVisibleWithPerms() {
            AuthContextHolder.setPerms(Set.of(
                    EmployeeService.PERM_PROFILE_READ_EDUCATION,
                    EmployeeService.PERM_PROFILE_READ_WORK,
                    EmployeeService.PERM_PROFILE_READ_FAMILY,
                    EmployeeService.PERM_PROFILE_READ_EMERGENCY));
            stubProfile();

            EmployeeProfileResponse p = service.get(100L);

            assertEquals(1, p.educations().size());
            assertEquals(1, p.works().size());
        }

        @Test
        @DisplayName("缺学历权限码 → 学历分组脱敏为空，其余分组不受影响")
        void masksMissingGroup() {
            AuthContextHolder.setPerms(Set.of(EmployeeService.PERM_PROFILE_READ_WORK));
            stubProfile();

            EmployeeProfileResponse p = service.get(100L);

            assertTrue(p.educations().isEmpty(), "无权限码的分组必须返回空集合");
            assertEquals(1, p.works().size(), "有权限码的分组不受影响");
        }

        @Test
        @DisplayName("权限上下文缺失（内部调用）→ 全部分组脱敏")
        void masksWhenNoContext() {
            stubProfile();

            EmployeeProfileResponse p = service.get(100L);

            assertTrue(p.educations().isEmpty());
            assertTrue(p.works().isEmpty());
        }
    }

    @Nested
    @DisplayName("分签独立整体替换")
    class SubProfileReplace {

        @Test
        @DisplayName("updateEducations → 仅替换教育经历，其余子表不动")
        void replacesOnlyEducations() {
            when(employeeMapper.selectById(100L)).thenReturn(employee());
            when(employmentService.current(100L)).thenReturn(null);
            when(profileService.load(100L)).thenReturn(snapshot());

            service.updateEducations(100L, List.of(new ProfileEducationDto(null, "北大", "数学", "硕士", null, null)));

            verify(profileService).replaceEducations(any(), any());
            verify(profileService, org.mockito.Mockito.never()).replaceWorks(any(), any());
            verify(profileService, org.mockito.Mockito.never()).replaceFamilies(any(), any());
            verify(profileService, org.mockito.Mockito.never()).replaceEmergencies(any(), any());
        }

        @Test
        @DisplayName("员工不存在 → NOT_FOUND，不触达子表")
        void rejectsMissingEmployee() {
            when(employeeMapper.selectById(404L)).thenReturn(null);

            org.junit.jupiter.api.Assertions.assertThrows(
                    com.hris.common.exception.BizException.class,
                    () -> service.updateWorks(404L, List.of()));

            verify(profileService, org.mockito.Mockito.never()).replaceWorks(any(), any());
        }

        @Test
        @DisplayName("updateFamilies 返回最新档案（复用读取链路）")
        void returnsFreshProfile() {
            when(employeeMapper.selectById(100L)).thenReturn(employee());
            when(employmentService.current(100L)).thenReturn(null);
            when(profileService.load(100L)).thenReturn(snapshot());

            EmployeeProfileResponse p = service.updateFamilies(100L, List.of());

            assertEquals("张三", p.name());
        }
    }
}
