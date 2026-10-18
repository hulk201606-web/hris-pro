package com.hris.employee.integration;

import com.hris.common.api.R;
import com.hris.common.exception.BizException;
import com.hris.common.exception.ErrorCode;
import com.hris.employee.client.OrgClient;
import com.hris.employee.dto.HcCheckResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

/**
 * HeadcountIntegration 单元测试（EMP 编制校验，docs/07 错误码 51204）。
 *
 * <p>**本批次安全影响最大的守卫**：编制校验是入职/调动的前置硬校验，实现上刻意选择
 * fail-closed——org 服务不可用或返回异常时抛 SYSTEM_ERROR，<b>而非返回 null 静默放行</b>。
 * 若有人把异常分支改成"降级返回 null"，超编入职/调动就会被静默通过，直接破坏编制管控。
 * 故此处对全部 4 条异常路径逐一断言「必须抛 SYSTEM_ERROR」。</p>
 */
@ExtendWith(MockitoExtension.class)
class HeadcountIntegrationTest {

    @Mock
    private OrgClient orgClient;

    private HeadcountIntegration integration;

    @BeforeEach
    void setUp() {
        integration = new HeadcountIntegration(orgClient);
    }

    @Test
    @DisplayName("正常返回 → 透传 org 的校验结论，不篡改 allowed/shortfall")
    void passesThroughOrgResult() {
        when(orgClient.check(anyLong(), anyLong(), any()))
                .thenReturn(R.ok(new HcCheckResponse(false, 10, 10, 3)));

        HcCheckResponse resp = integration.check(1L, 2L, 3);

        assertFalse(resp.allowed(), "应原样透达 org 的拦截结论");
        assertEquals(3, resp.shortfall());
    }

    @Test
    @DisplayName("编制充足时透传 allowed=true")
    void passesThroughAllowedResult() {
        when(orgClient.check(anyLong(), anyLong(), any()))
                .thenReturn(R.ok(new HcCheckResponse(true, 10, 3, 0)));

        assertTrue(integration.check(1L, 2L, 1).allowed());
    }

    @Test
    @DisplayName("Feign 调用抛异常 → SYSTEM_ERROR（fail-closed，不得静默放行）")
    void throwsWhenClientThrows() {
        when(orgClient.check(anyLong(), anyLong(), any()))
                .thenThrow(new RuntimeException("connection refused"));

        BizException e = assertThrows(BizException.class, () -> integration.check(1L, 2L, 1));

        assertEquals(ErrorCode.SYSTEM_ERROR.getCode(), e.getCode(),
                "org 不可用时必须 fail-closed；若放行会导致超编入职绕过编制管控");
    }

    @Test
    @DisplayName("响应体为 null → SYSTEM_ERROR（不得视为通过）")
    void throwsWhenResponseNull() {
        when(orgClient.check(anyLong(), anyLong(), any())).thenReturn(null);

        BizException e = assertThrows(BizException.class, () -> integration.check(1L, 2L, 1));

        assertEquals(ErrorCode.SYSTEM_ERROR.getCode(), e.getCode());
    }

    @Test
    @DisplayName("业务失败响应（isSuccess=false）→ SYSTEM_ERROR")
    void throwsWhenBusinessFailed() {
        when(orgClient.check(anyLong(), anyLong(), any()))
                .thenReturn(R.fail(ErrorCode.PARAM_INVALID));

        BizException e = assertThrows(BizException.class, () -> integration.check(1L, 2L, 1));

        assertEquals(ErrorCode.SYSTEM_ERROR.getCode(), e.getCode());
    }

    @Test
    @DisplayName("成功响应但 data 为 null → SYSTEM_ERROR（不得 NPE，也不得放行）")
    void throwsWhenDataNull() {
        when(orgClient.check(anyLong(), anyLong(), any())).thenReturn(R.ok(null));

        BizException e = assertThrows(BizException.class, () -> integration.check(1L, 2L, 1));

        assertEquals(ErrorCode.SYSTEM_ERROR.getCode(), e.getCode());
    }

    @Test
    @DisplayName("异常信息为可读中文，便于前端提示用户稍后重试")
    void messageIsUserFriendly() {
        when(orgClient.check(anyLong(), anyLong(), any()))
                .thenThrow(new RuntimeException("connection refused"));

        BizException e = assertThrows(BizException.class, () -> integration.check(1L, 2L, 1));

        assertTrue(e.getMessage().contains("编制校验"),
                "异常信息应指明是编制校验失败，实际=" + e.getMessage());
    }
}