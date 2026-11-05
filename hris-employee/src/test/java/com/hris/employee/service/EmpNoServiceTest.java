package com.hris.employee.service;

import com.hris.common.api.R;
import com.hris.employee.client.PlatformConfigClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 租户级工号策略（EMP-F-002）：模板驱动 / 缺省兼容 / 配置读取失败降级。
 */
@ExtendWith(MockitoExtension.class)
class EmpNoServiceTest {

    @Mock
    private PlatformConfigClient configClient;
    @Mock
    private EmpNoSeqAllocator seqAllocator;

    private EmpNoService service;

    @BeforeEach
    void setUp() {
        service = new EmpNoService(configClient, seqAllocator);
    }

    @Test
    @DisplayName("配置模板 → 走序号段并渲染模板")
    void usesTenantPattern() {
        when(configClient.getTenantConfig(EmpNoService.CONFIG_KEY))
                .thenReturn(R.ok("{PREFIX}{YYYY}{SEQ:4}"));
        when(seqAllocator.allocate(eq("emp"), anyInt())).thenReturn(7);

        String empNo = service.next("EMP");

        assertTrue(empNo.matches("emp\\d{4}0007"), "实际=" + empNo);
    }

    @Test
    @DisplayName("无配置 → 保持原雪花前缀策略（不触碰序号表）")
    void fallsBackToSnowflakeWhenAbsent() {
        when(configClient.getTenantConfig(EmpNoService.CONFIG_KEY)).thenReturn(R.ok(null));

        String empNo = service.next("EMP");

        assertTrue(empNo.startsWith("emp"), "实际=" + empNo);
        assertTrue(empNo.substring(3).chars().allMatch(Character::isDigit), "雪花后缀应为数字");
        verify(seqAllocator, never()).allocate(eq("emp"), anyInt());
    }

    @Test
    @DisplayName("配置读取异常 → 降级雪花，建档不中断")
    void fallsBackOnClientFailure() {
        when(configClient.getTenantConfig(EmpNoService.CONFIG_KEY))
                .thenThrow(new RuntimeException("platform down"));

        String empNo = service.next(null);

        assertTrue(empNo.startsWith("e"), "实际=" + empNo);
        verify(seqAllocator, never()).allocate(eq("e"), anyInt());
    }

    @Test
    @DisplayName("模板非法（缺 SEQ）→ 回退雪花，避免重复工号")
    void fallsBackOnInvalidPattern() {
        when(configClient.getTenantConfig(EmpNoService.CONFIG_KEY)).thenReturn(R.ok("{PREFIX}{YYYY}"));

        String empNo = service.next("E");

        assertTrue(empNo.startsWith("e"), "实际=" + empNo);
        verify(seqAllocator, never()).allocate(eq("e"), anyInt());
    }

    @Test
    @DisplayName("不同前缀独立分配序号")
    void prefixIsIndependent() {
        when(configClient.getTenantConfig(EmpNoService.CONFIG_KEY)).thenReturn(R.ok("{PREFIX}{SEQ:3}"));
        when(seqAllocator.allocate("a", java.time.Year.now().getValue())).thenReturn(1);
        when(seqAllocator.allocate("b", java.time.Year.now().getValue())).thenReturn(2);

        assertEquals("a001", service.next("a"));
        assertEquals("b002", service.next("b"));
    }
}
