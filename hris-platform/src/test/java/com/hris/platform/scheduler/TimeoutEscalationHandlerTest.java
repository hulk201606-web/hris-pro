package com.hris.platform.scheduler;

import com.hris.common.api.R;
import com.hris.common.trace.TraceContext;
import com.hris.platform.client.FlowOpsClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

/**
 * TimeoutEscalationHandler 单元测试（docs/19 §10.5 深化：调度接入）。
 *
 * <p>守卫：成功返回摘要（扫描/升级/幂等跳过计数）；下游失败码与空载荷以异常透传
 * （由调度器记 FAILED 触发重试）；缺租户上下文拒绝执行。</p>
 */
@ExtendWith(MockitoExtension.class)
class TimeoutEscalationHandlerTest {

    @Mock
    private FlowOpsClient flowOpsClient;

    private TimeoutEscalationHandler handler;

    @BeforeEach
    void setUp() {
        handler = new TimeoutEscalationHandler(flowOpsClient);
        TraceContext.setTenantId(100001L);
    }

    @AfterEach
    void clear() {
        TraceContext.clear();
    }

    @Test
    @DisplayName("key 与注册表 handler 一致，描述非空")
    void keyAndDescription() {
        assertEquals("TIMEOUT_ESCALATION", handler.key());
        assertTrue(handler.description() != null && !handler.description().isBlank());
    }

    @Test
    @DisplayName("成功 → 摘要携带 scanned/escalated/skippedIdempotent 计数")
    void returnsSummary() throws Exception {
        when(flowOpsClient.escalateOverdue()).thenReturn(R.ok(Map.of(
                "scanned", 5, "escalated", 2, "skippedIdempotent", 1)));

        String summary = handler.execute();

        assertTrue(summary.contains("5"), "实际=" + summary);
        assertTrue(summary.contains("2"), "实际=" + summary);
        assertTrue(summary.contains("1"), "实际=" + summary);
    }

    @Test
    @DisplayName("下游返回失败码 → 异常透传（调度器记 FAILED）")
    void propagatesDownstreamFailure() {
        R<Map<String, Integer>> resp = new R<>();
        resp.setCode(500);
        resp.setMessage("flow db error");
        when(flowOpsClient.escalateOverdue()).thenReturn(resp);

        assertThrows(IllegalStateException.class, () -> handler.execute());
    }

    @Test
    @DisplayName("下游返回空载荷 → 异常透传")
    void propagatesNullPayload() {
        when(flowOpsClient.escalateOverdue()).thenReturn(R.ok());

        assertThrows(IllegalStateException.class, () -> handler.execute());
    }

    @Test
    @DisplayName("缺少租户上下文 → 拒绝执行")
    void rejectsMissingTenant() {
        TraceContext.setTenantId(null);

        assertThrows(IllegalStateException.class, () -> handler.execute());
    }
}
