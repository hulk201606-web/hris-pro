package com.hris.employee.service;

import com.hris.common.exception.BizException;
import com.hris.common.exception.ErrorCode;
import com.hris.employee.dto.HcCheckResponse;
import com.hris.employee.dto.OnboardRequest;
import com.hris.employee.entity.Employee;
import com.hris.employee.enums.EmployeeStatus;
import com.hris.employee.enums.LifecycleType;
import com.hris.employee.integration.HeadcountIntegration;
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
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * OnboardingService 单元测试（EMP-F-003/004/006 入职办理）。
 *
 * <p>三道守卫：①员工必须待入职（PENDING）②编制校验 fail-closed（不足抛 EMP_HEADCOUNT_SHORT）
 * ③载荷组装（username 缺省取工号、password 缺省随机 12 位）。</p>
 */
@ExtendWith(MockitoExtension.class)
class OnboardingServiceTest {

    @Mock
    private EmployeeMapper employeeMapper;
    @Mock
    private HeadcountIntegration headcountIntegration;
    @Mock
    private LifecycleCaseService lifecycleCaseService;

    private OnboardingService service;

    @BeforeEach
    void setUp() {
        service = new OnboardingService(employeeMapper, headcountIntegration, lifecycleCaseService);
    }

    private Employee employee(int status) {
        Employee e = new Employee();
        e.setId(100L);
        e.setEmpNo("E001");
        e.setStatus(status);
        return e;
    }

    private OnboardRequest request(String username, String password) {
        return new OnboardRequest(100L, 1L, 2L, 3L, 4L, "FULLTIME", 99L,
                LocalDate.now().plusDays(7), username, password, List.of("EMP"), "入职");
    }

    @Nested
    @DisplayName("状态守卫：仅待入职员工可发起入职")
    class StatusGuard {

        @Test
        @DisplayName("员工不存在 → NOT_FOUND，不落单")
        void notFound() {
            when(employeeMapper.selectById(404L)).thenReturn(null);

            BizException e = assertThrows(BizException.class,
                    () -> service.create(new OnboardRequest(404L, 1L, 2L, 3L, 4L, "FULLTIME", 99L,
                            LocalDate.now(), null, null, List.of(), null)));

            assertEquals(ErrorCode.NOT_FOUND.getCode(), e.getCode());
            verify(lifecycleCaseService, never()).createCase(anyLong(), any(), any(), any(), any());
        }

        @Test
        @DisplayName("非待入职员工 → EMP_ERROR，不落单")
        void rejectsNonPending() {
            when(employeeMapper.selectById(100L)).thenReturn(employee(EmployeeStatus.ACTIVE.getCode()));

            BizException e = assertThrows(BizException.class, () -> service.create(request(null, null)));

            assertEquals(ErrorCode.EMP_ERROR.getCode(), e.getCode());
            verify(lifecycleCaseService, never()).createCase(anyLong(), any(), any(), any(), any());
        }
    }

    @Nested
    @DisplayName("编制守卫：fail-closed")
    class HeadcountGuard {

        @Test
        @DisplayName("编制不足 → EMP_HEADCOUNT_SHORT，不落单")
        void rejectsWhenHeadcountShort() {
            when(employeeMapper.selectById(100L)).thenReturn(employee(EmployeeStatus.PENDING.getCode()));
            when(headcountIntegration.check(any(), any(), anyInt())).thenReturn(new HcCheckResponse(false, 10, 10, 1));

            BizException e = assertThrows(BizException.class, () -> service.create(request(null, null)));

            assertEquals(ErrorCode.EMP_HEADCOUNT_SHORT.getCode(), e.getCode());
            verify(lifecycleCaseService, never()).createCase(anyLong(), any(), any(), any(), any());
        }
    }

    @Nested
    @DisplayName("成功路径与载荷组装")
    class Create {

        @Test
        @DisplayName("username 缺省取工号；password 缺省随机 12 位；落 ONBOARD 单")
        void defaultsUsernameAndPassword() {
            when(employeeMapper.selectById(100L)).thenReturn(employee(EmployeeStatus.PENDING.getCode()));
            when(headcountIntegration.check(any(), any(), anyInt())).thenReturn(new HcCheckResponse(true, 10, 9, 0));

            service.create(request(null, null));

            ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
            verify(lifecycleCaseService).createCase(eq(100L), eq(LifecycleType.ONBOARD), any(), captor.capture(), eq("入职"));
            Map<String, Object> payload = captor.getValue();
            assertEquals("E001", payload.get("username"), "username 缺省应取工号");
            assertEquals(12, ((String) payload.get("password")).length(), "password 缺省应随机 12 位");
            assertEquals(2L, payload.get("deptId"));
        }

        @Test
        @DisplayName("username 显式传入 → 去首尾空白并转小写")
        void normalizesUsername() {
            when(employeeMapper.selectById(100L)).thenReturn(employee(EmployeeStatus.PENDING.getCode()));
            when(headcountIntegration.check(any(), any(), anyInt())).thenReturn(new HcCheckResponse(true, 10, 9, 0));

            service.create(request(" USER1 ", "secret123"));

            ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
            verify(lifecycleCaseService).createCase(eq(100L), eq(LifecycleType.ONBOARD), any(), captor.capture(), eq("入职"));
            assertEquals("user1", captor.getValue().get("username"));
            assertEquals("secret123", captor.getValue().get("password"));
        }
    }
}
