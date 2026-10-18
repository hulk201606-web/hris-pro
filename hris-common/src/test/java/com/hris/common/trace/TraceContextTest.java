package com.hris.common.trace;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * TraceContext 单元测试（多租户/链路透传核心）。
 *
 * <p>ThreadLocal 的 set/get/clear 契约是租户隔离的地基：跨模块 Feign 调用依赖
 * X-Tenant-Id/X-Trace-Id 透传（见内部端点约定），clear 遗漏会导致线程复用时的
 * 跨租户串数据——本测试锁住 set→get→clear 全链路。</p>
 */
class TraceContextTest {

    @AfterEach
    void tearDown() {
        TraceContext.clear();
    }

    @Test
    @DisplayName("traceId / tenantId / userId set 后 get 原值返回")
    void setThenGet() {
        TraceContext.setTraceId("trace-abc");
        TraceContext.setTenantId(100001L);
        TraceContext.setUserId(200001L);
        assertEquals("trace-abc", TraceContext.getTraceId());
        assertEquals(100001L, TraceContext.getTenantId());
        assertEquals(200001L, TraceContext.getUserId());
    }

    @Test
    @DisplayName("clear 后三项全部归 null（防线程复用串租户）")
    void clearRemovesAll() {
        TraceContext.setTraceId("t");
        TraceContext.setTenantId(1L);
        TraceContext.setUserId(2L);
        TraceContext.clear();
        assertNull(TraceContext.getTraceId());
        assertNull(TraceContext.getTenantId());
        assertNull(TraceContext.getUserId());
    }

    @Test
    @DisplayName("未 set 时 get 返回 null（新线程默认态）")
    void defaultsAreNull() {
        assertNull(TraceContext.getTraceId());
        assertNull(TraceContext.getTenantId());
        assertNull(TraceContext.getUserId());
    }
}
