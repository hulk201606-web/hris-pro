package com.hris.employee.service;

import com.hris.employee.entity.Employment;
import com.hris.employee.mapper.EmploymentMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * EmploymentService 单元测试 —— 雇佣关系版本链（emp_employment）。
 *
 * <p>现行雇佣关系 = status=1 且 effective_to IS NULL。业务逻辑集中在
 * closeCurrent（关闭现行并置 status=0 + effectiveTo）与 open（开启新版本 status=1）的字段语义。</p>
 */
@ExtendWith(MockitoExtension.class)
class EmploymentServiceTest {

    @Mock
    private EmploymentMapper employmentMapper;

    private EmploymentService service;

    @BeforeEach
    void setUp() {
        service = new EmploymentService(employmentMapper);
    }

    private Employment currentEmployment() {
        Employment e = new Employment();
        e.setId(1L);
        e.setEmpId(100L);
        e.setDeptId(2L);
        e.setStatus(1);
        return e;
    }

    @Nested
    @DisplayName("closeCurrent：关闭现行雇佣关系")
    class CloseCurrent {

        @Test
        @DisplayName("无现行雇佣关系 → 直接返回，不更新")
        void noCurrentDoesNothing() {
            when(employmentMapper.selectOne(any())).thenReturn(null);

            service.closeCurrent(100L, LocalDate.of(2026, 1, 31));

            verify(employmentMapper, never()).updateById(any(Employment.class));
        }

        @Test
        @DisplayName("存在现行关系 → 置 status=0 并写 effectiveTo")
        void closesCurrent() {
            when(employmentMapper.selectOne(any())).thenReturn(currentEmployment());

            service.closeCurrent(100L, LocalDate.of(2026, 1, 31));

            ArgumentCaptor<Employment> captor = ArgumentCaptor.forClass(Employment.class);
            verify(employmentMapper).updateById(captor.capture());
            assertEquals(0, captor.getValue().getStatus(), "关闭后 status 应为 0");
            assertEquals(LocalDate.of(2026, 1, 31), captor.getValue().getEffectiveTo());
        }
    }

    @Nested
    @DisplayName("open：开启新雇佣关系版本")
    class Open {

        @Test
        @DisplayName("新版本 status=1、字段完整映射、effectiveTo 保持 null")
        void opensNewVersion() {
            service.open(100L, 1L, 2L, 3L, 4L, "FULLTIME", 99L, LocalDate.of(2026, 2, 1));

            ArgumentCaptor<Employment> captor = ArgumentCaptor.forClass(Employment.class);
            verify(employmentMapper).insert(captor.capture());
            assertEquals(1, captor.getValue().getStatus(), "新版本 status 应为 1（现行）");
            assertEquals(100L, captor.getValue().getEmpId());
            assertEquals(1L, captor.getValue().getLegalEntityId());
            assertEquals(2L, captor.getValue().getDeptId());
            assertEquals(3L, captor.getValue().getPositionId());
            assertEquals(4L, captor.getValue().getGradeId());
            assertEquals("FULLTIME", captor.getValue().getHireType());
            assertEquals(99L, captor.getValue().getSupervisorEmpId());
            assertEquals(LocalDate.of(2026, 2, 1), captor.getValue().getEffectiveFrom());
            assertEquals(null, captor.getValue().getEffectiveTo(), "新版本 effectiveTo 应为 null（现行）");
        }
    }
}
