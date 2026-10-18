package com.hris.performance.service;

import com.hris.common.api.R;
import com.hris.common.exception.BizException;
import com.hris.common.exception.ErrorCode;
import com.hris.performance.client.EmployeeClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

/**
 * ReviewContextService 单元测试（PRF-F-020 绩效评估上下文跨域取数）。
 *
 * <p>锁定行为：员工简报成功即返回；Feign 失败/空数据一律抛 PRF_ERROR
 * （评估单不可在缺员工上下文时创建，避免绩效域冗余存储员工主数据）。</p>
 */
@ExtendWith(MockitoExtension.class)
class ReviewContextServiceTest {

    @Mock
    private EmployeeClient employeeClient;

    private ReviewContextService service;

    @BeforeEach
    void setUp() {
        service = new ReviewContextService(employeeClient);
    }

    @Test
    @DisplayName("取数成功 → 返回员工档案简报")
    void returnsBrief() {
        Map<String, Object> brief = Map.of("empId", 200001L, "name", "张伟", "status", 0);
        when(employeeClient.profileBrief(200001L)).thenReturn(R.ok(brief));

        Map<String, Object> result = service.context(200001L);

        assertEquals("张伟", result.get("name"));
    }

    @Test
    @DisplayName("Feign 返回失败 → PRF_ERROR")
    void throwsWhenClientFails() {
        when(employeeClient.profileBrief(200001L)).thenReturn(R.fail(ErrorCode.EMP_ERROR));

        BizException e = assertThrows(BizException.class, () -> service.context(200001L));
        assertEquals(ErrorCode.PRF_ERROR.getCode(), e.getCode());
    }

    @Test
    @DisplayName("成功但 data 为 null → PRF_ERROR（防止下游 NPE）")
    void throwsWhenDataNull() {
        when(employeeClient.profileBrief(200001L)).thenReturn(R.ok(null));

        BizException e = assertThrows(BizException.class, () -> service.context(200001L));
        assertEquals(ErrorCode.PRF_ERROR.getCode(), e.getCode());
    }

    @Test
    @DisplayName("Feign 返回 null 对象 → PRF_ERROR")
    void throwsWhenResponseNull() {
        when(employeeClient.profileBrief(200001L)).thenReturn(null);

        BizException e = assertThrows(BizException.class, () -> service.context(200001L));
        assertEquals(ErrorCode.PRF_ERROR.getCode(), e.getCode());
    }
}
