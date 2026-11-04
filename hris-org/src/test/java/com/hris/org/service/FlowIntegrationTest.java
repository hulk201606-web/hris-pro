package com.hris.org.service;

import com.hris.common.api.R;
import com.hris.org.client.FlowClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * FlowIntegration 单元测试 —— 流程中台集成门面（MVP 降级语义）。
 *
 * <p>核心业务逻辑是「flow 未就绪时降级为仅落单、不阻断组织调整申请」：
 * 任何响应缺失（null 响应 / null data / 缺 instanceId）或调用异常，一律返回 null 而非抛错。</p>
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

    private R<Map<String, Object>> respWith(String instanceId) {
        R<Map<String, Object>> resp = new R<>();
        Map<String, Object> data = new HashMap<>();
        data.put("instanceId", instanceId);
        resp.setData(data);
        return resp;
    }

    @Nested
    @DisplayName("startOrgAdjust：发起组织调整流程")
    class StartOrgAdjust {

        @Test
        @DisplayName("flow 正常返回 instanceId → 原样返回")
        void returnsInstanceId() {
            when(flowClient.startInstance(any())).thenReturn(respWith("flow-123"));

            String id = integration.startOrgAdjust(Map.of("bizType", "ORG_ADJUST"));

            assertEquals("flow-123", id);
        }

        @Test
        @DisplayName("flow 响应为 null → 降级返回 null")
        void nullResponseDegradesToNull() {
            when(flowClient.startInstance(any())).thenReturn(null);

            assertNull(integration.startOrgAdjust(Map.of()));
        }

        @Test
        @DisplayName("flow data 为 null → 降级返回 null")
        void nullDataDegradesToNull() {
            R<Map<String, Object>> resp = new R<>();
            resp.setData(null);
            when(flowClient.startInstance(any())).thenReturn(resp);

            assertNull(integration.startOrgAdjust(Map.of()));
        }

        @Test
        @DisplayName("data 缺 instanceId → 降级返回 null")
        void missingInstanceIdDegradesToNull() {
            R<Map<String, Object>> resp = new R<>();
            resp.setData(Map.of("bizType", "ORG_ADJUST"));
            when(flowClient.startInstance(any())).thenReturn(resp);

            assertNull(integration.startOrgAdjust(Map.of()));
        }

        @Test
        @DisplayName("flow 调用抛异常 → 降级返回 null（不阻断落单）")
        void exceptionDegradesToNull() {
            when(flowClient.startInstance(any())).thenThrow(new RuntimeException("flow down"));

            assertNull(integration.startOrgAdjust(Map.of()));
        }
    }
}
