package com.hris.compensation.service;

import com.hris.common.api.R;
import com.hris.compensation.client.FlowClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * FlowIntegration fail-open 单测：flow 服务异常时降级返回 null，不向业务侧抛异常。
 */
@ExtendWith(MockitoExtension.class)
class FlowIntegrationTest {

    @Mock
    private FlowClient flowClient;

    private FlowIntegration integration;

    @BeforeEach
    void setUp() {
        integration = new FlowIntegration(flowClient);
    }

    @Test
    @DisplayName("flow 发起成功 → 返回 instanceId")
    void returnsInstanceId() {
        when(flowClient.startInstance(any())).thenReturn(
                R.ok(Map.of("instanceId", "inst-9", "bizType", "CMP_PAYROLL")));

        String id = integration.start("CMP_PAYROLL", "700001", Map.of("periodCode", "2026-01"));

        assertEquals("inst-9", id);
    }

    @Test
    @DisplayName("FlowClient 抛异常 → fail-open 返回 null，不阻断业务")
    void failsOpenOnClientException() {
        when(flowClient.startInstance(any())).thenThrow(new RuntimeException("connect refused"));

        assertNull(integration.start("CMP_PAYROLL", "700001", Map.of()));
    }

    @Test
    @DisplayName("响应体缺 instanceId → 返回 null（降级）")
    void failsOpenWhenDataMissing() {
        when(flowClient.startInstance(any())).thenReturn(R.ok(null));

        assertNull(integration.start("CMP_PAYROLL", "700001", Map.of()));
    }
}
