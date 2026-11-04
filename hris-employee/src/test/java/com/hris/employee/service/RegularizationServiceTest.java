package com.hris.employee.service;

import com.hris.common.exception.BizException;
import com.hris.common.exception.ErrorCode;
import com.hris.employee.dto.RegularizeRequest;
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
 * RegularizationService 单元测试（EMP-F-008 转正办理）。
 *
 * <p>守卫：仅试用期（PROBATION）员工可发起转正流程。</p>
 */
@ExtendWith(MockitoExtension.class)
class RegularizationServiceTest {

    @Mock
    private EmployeeMapper employeeMapper;
    @Mock
    private LifecycleCaseService lifecycleCaseService;

    private RegularizationService service;

    @BeforeEach
    void setUp() {
        service = new RegularizationService(employeeMapper, lifecycleCaseService);
    }

    private Employee employee(int status) {
        Employee e = new Employee();
        e.setId(100L);
        e.setEmpNo("E001");
        e.setStatus(status);
        return e;
    }

    private RegularizeRequest request() {
        return new RegularizeRequest(100L, LocalDate.now().plusDays(10), "表现优秀", "按期转正");
    }

    @Nested
    @DisplayName("状态守卫：仅试用期员工可发起转正")
    class StatusGuard {

        @Test
        @DisplayName("员工不存在 → NOT_FOUND")
        void notFound() {
            when(employeeMapper.selectById(404L)).thenReturn(null);

            BizException e = assertThrows(BizException.class, () -> service.create(
                    new RegularizeRequest(404L, LocalDate.now(), null, null)));

            assertEquals(ErrorCode.NOT_FOUND.getCode(), e.getCode());
            verify(lifecycleCaseService, never()).createCase(anyLong(), any(), any(), any(), any());
        }

        @Test
        @DisplayName("非试用期员工 → EMP_ERROR，不落单")
        void rejectsNonProbation() {
            when(employeeMapper.selectById(100L)).thenReturn(employee(EmployeeStatus.ACTIVE.getCode()));

            BizException e = assertThrows(BizException.class, () -> service.create(request()));

            assertEquals(ErrorCode.EMP_ERROR.getCode(), e.getCode());
            verify(lifecycleCaseService, never()).createCase(anyLong(), any(), any(), any(), any());
        }
    }

    @Nested
    @DisplayName("成功路径：落 REGULAR 单")
    class Create {

        @Test
        @DisplayName("试用期员工 → 落 REGULAR 单，载荷携带评语与原因")
        void createsRegularCase() {
            when(employeeMapper.selectById(100L)).thenReturn(employee(EmployeeStatus.PROBATION.getCode()));

            service.create(request());

            ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
            verify(lifecycleCaseService).createCase(eq(100L), eq(LifecycleType.REGULAR),
                    eq(LocalDate.now().plusDays(10)), captor.capture(), eq("按期转正"));
            assertEquals("表现优秀", captor.getValue().get("comment"));
        }
    }
}
