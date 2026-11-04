package com.hris.employee.service;

import com.hris.common.exception.BizException;
import com.hris.common.exception.ErrorCode;
import com.hris.employee.dto.PreHireLinkResponse;
import com.hris.employee.entity.Employee;
import com.hris.employee.mapper.EmployeeMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * PreHireService 单元测试（EMP-F-005 入职资料自助填报链接）。
 *
 * <p>业务逻辑：员工存在性守卫 + 一次性 token / 链接 / 7 天有效期的生成。</p>
 */
@ExtendWith(MockitoExtension.class)
class PreHireServiceTest {

    @Mock
    private EmployeeMapper employeeMapper;

    private PreHireService service;

    @BeforeEach
    void setUp() {
        service = new PreHireService(employeeMapper);
    }

    private Employee employee() {
        Employee e = new Employee();
        e.setId(100L);
        e.setEmpNo("E001");
        return e;
    }

    @Nested
    @DisplayName("createLink：生成自助填报链接")
    class CreateLink {

        @Test
        @DisplayName("员工不存在 → NOT_FOUND")
        void notFound() {
            when(employeeMapper.selectById(404L)).thenReturn(null);

            BizException e = assertThrows(BizException.class, () -> service.createLink(404L));

            assertEquals(ErrorCode.NOT_FOUND.getCode(), e.getCode());
        }

        @Test
        @DisplayName("存在 → 生成一次性 token（32 位无连字符）+ 链接 + 7 天有效期")
        void generatesLink() {
            when(employeeMapper.selectById(100L)).thenReturn(employee());

            PreHireLinkResponse resp = service.createLink(100L);

            assertEquals(100L, resp.empId());
            assertNotNull(resp.token());
            assertEquals(32, resp.token().length(), "UUID 去连字符应为 32 位");
            assertTrue(resp.link().startsWith("/pre-hire/apply?token="), "链接应携带 token 查询参数");
            assertTrue(resp.expireAt().isAfter(LocalDateTime.now()), "有效期应在未来");
            assertTrue(resp.expireAt().isBefore(LocalDateTime.now().plusDays(8)), "有效期应约为 7 天");
        }
    }
}
