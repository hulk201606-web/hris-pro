package com.hris.employee.service;

import com.hris.common.exception.BizException;
import com.hris.common.exception.ErrorCode;
import com.hris.employee.dto.ResignRequest;
import com.hris.employee.entity.Employee;
import com.hris.employee.enums.EmployeeStatus;
import com.hris.employee.enums.LifecycleType;
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
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * ResignationService 单元测试（EMP-F-011 离职办理）。
 *
 * <p>守卫：仅在职（ACTIVE）或试用（PROBATION）员工可发起离职流程；离职中/已离职等状态一律拒绝。</p>
 */
@ExtendWith(MockitoExtension.class)
class ResignationServiceTest {

    @Mock
    private EmployeeMapper employeeMapper;
    @Mock
    private LifecycleCaseService lifecycleCaseService;

    private ResignationService service;

    @BeforeEach
    void setUp() {
        service = new ResignationService(employeeMapper, lifecycleCaseService);
    }

    private Employee employee(int status) {
        Employee e = new Employee();
        e.setId(100L);
        e.setEmpNo("E001");
        e.setStatus(status);
        return e;
    }

    private ResignRequest request() {
        return new ResignRequest(100L, LocalDate.now().plusDays(30), "VOLUNTARY", "个人原因");
    }

    @Nested
    @DisplayName("状态守卫：仅在职/试用员工可发起离职")
    class StatusGuard {

        @Test
        @DisplayName("员工不存在 → NOT_FOUND")
        void notFound() {
            when(employeeMapper.selectById(404L)).thenReturn(null);

            BizException e = assertThrows(BizException.class, () -> service.create(
                    new ResignRequest(404L, LocalDate.now(), null, null)));

            assertEquals(ErrorCode.NOT_FOUND.getCode(), e.getCode());
            verify(lifecycleCaseService, never()).createCase(anyLong(), any(), any(), any(), any());
        }

        @Test
        @DisplayName("已离职员工 → EMP_ERROR，不落单")
        void rejectsResigned() {
            when(employeeMapper.selectById(100L)).thenReturn(employee(EmployeeStatus.RESIGNED.getCode()));

            BizException e = assertThrows(BizException.class, () -> service.create(request()));

            assertEquals(ErrorCode.EMP_ERROR.getCode(), e.getCode());
            verify(lifecycleCaseService, never()).createCase(anyLong(), any(), any(), any(), any());
        }
    }

    @Nested
    @DisplayName("成功路径：落 RESIGN 单")
    class Create {

        @Test
        @DisplayName("在职员工 → 落 RESIGN 单")
        void createsResignCaseForActive() {
            when(employeeMapper.selectById(100L)).thenReturn(employee(EmployeeStatus.ACTIVE.getCode()));

            service.create(request());

            verify(lifecycleCaseService).createCase(eq(100L), eq(LifecycleType.RESIGN),
                    eq(LocalDate.now().plusDays(30)), any(), eq("个人原因"));
        }

        @Test
        @DisplayName("试用期员工 → 同样可发起离职")
        void createsResignCaseForProbation() {
            when(employeeMapper.selectById(100L)).thenReturn(employee(EmployeeStatus.PROBATION.getCode()));

            service.create(request());

            ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
            verify(lifecycleCaseService).createCase(eq(100L), eq(LifecycleType.RESIGN), any(), captor.capture(), eq("个人原因"));
            assertEquals("VOLUNTARY", captor.getValue().get("resignType"));
        }
    }
}
