package com.hris.employee.service;

import com.hris.common.exception.BizException;
import com.hris.common.exception.ErrorCode;
import com.hris.employee.dto.HcCheckResponse;
import com.hris.employee.dto.TransferRequest;
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
 * TransferService 单元测试（EMP-F-009 调动办理）。
 *
 * <p>守卫：①员工必须在职（ACTIVE）②目标部门/岗位编制校验 fail-closed。生效日前不改变主状态。</p>
 */
@ExtendWith(MockitoExtension.class)
class TransferServiceTest {

    @Mock
    private EmployeeMapper employeeMapper;
    @Mock
    private HeadcountIntegration headcountIntegration;
    @Mock
    private LifecycleCaseService lifecycleCaseService;

    private TransferService service;

    @BeforeEach
    void setUp() {
        service = new TransferService(employeeMapper, headcountIntegration, lifecycleCaseService);
    }

    private Employee employee(int status) {
        Employee e = new Employee();
        e.setId(100L);
        e.setEmpNo("E001");
        e.setStatus(status);
        return e;
    }

    private TransferRequest request() {
        return new TransferRequest(100L, 9L, 8L, 4L, 99L, LocalDate.now().plusDays(30), "组织调整");
    }

    @Nested
    @DisplayName("状态守卫：仅在职员工可发起调动")
    class StatusGuard {

        @Test
        @DisplayName("员工不存在 → NOT_FOUND")
        void notFound() {
            when(employeeMapper.selectById(404L)).thenReturn(null);

            BizException e = assertThrows(BizException.class, () -> service.create(
                    new TransferRequest(404L, 9L, 8L, 4L, 99L, LocalDate.now(), null)));

            assertEquals(ErrorCode.NOT_FOUND.getCode(), e.getCode());
            verify(lifecycleCaseService, never()).createCase(anyLong(), any(), any(), any(), any());
        }

        @Test
        @DisplayName("非在职员工 → EMP_ERROR，不落单")
        void rejectsNonActive() {
            when(employeeMapper.selectById(100L)).thenReturn(employee(EmployeeStatus.PROBATION.getCode()));

            BizException e = assertThrows(BizException.class, () -> service.create(request()));

            assertEquals(ErrorCode.EMP_ERROR.getCode(), e.getCode());
            verify(lifecycleCaseService, never()).createCase(anyLong(), any(), any(), any(), any());
        }
    }

    @Nested
    @DisplayName("编制守卫：目标部门/岗位 fail-closed")
    class HeadcountGuard {

        @Test
        @DisplayName("目标编制不足 → EMP_HEADCOUNT_SHORT，不落单")
        void rejectsWhenHeadcountShort() {
            when(employeeMapper.selectById(100L)).thenReturn(employee(EmployeeStatus.ACTIVE.getCode()));
            when(headcountIntegration.check(any(), any(), anyInt())).thenReturn(new HcCheckResponse(false, 10, 10, 2));

            BizException e = assertThrows(BizException.class, () -> service.create(request()));

            assertEquals(ErrorCode.EMP_HEADCOUNT_SHORT.getCode(), e.getCode());
            verify(lifecycleCaseService, never()).createCase(anyLong(), any(), any(), any(), any());
        }
    }

    @Nested
    @DisplayName("成功路径：落 TRANSFER 单并组装目标载荷")
    class Create {

        @Test
        @DisplayName("编制充足 → 落 TRANSFER 单，载荷携带目标部门/岗位/职级/上级")
        void createsTransferCase() {
            when(employeeMapper.selectById(100L)).thenReturn(employee(EmployeeStatus.ACTIVE.getCode()));
            when(headcountIntegration.check(any(), any(), anyInt())).thenReturn(new HcCheckResponse(true, 10, 8, 0));

            service.create(request());

            ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
            verify(lifecycleCaseService).createCase(eq(100L), eq(LifecycleType.TRANSFER),
                    eq(LocalDate.now().plusDays(30)), captor.capture(), eq("组织调整"));
            Map<String, Object> payload = captor.getValue();
            assertEquals(9L, payload.get("targetDeptId"));
            assertEquals(8L, payload.get("targetPositionId"));
            assertEquals(4L, payload.get("targetGradeId"));
            assertEquals(99L, payload.get("targetSupervisorEmpId"));
        }
    }
}
