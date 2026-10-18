package com.hris.common.trace;

/**
 * 链路追踪上下文。存储当前请求的 traceId / tenantId / userId。
 *
 * <p>注：当前用 ThreadLocal 实现，适用于同步调用链；异步线程池 / 消息消费场景
 * 需替换为 TransmittableThreadLocal（后续 Phase 3 接入 RocketMQ 消费时处理）。</p>
 */
public final class TraceContext {

    private static final ThreadLocal<String> TRACE_ID = new ThreadLocal<>();
    private static final ThreadLocal<Long> TENANT_ID = new ThreadLocal<>();
    private static final ThreadLocal<Long> USER_ID = new ThreadLocal<>();

    private TraceContext() {
    }

    public static void setTraceId(String traceId) {
        TRACE_ID.set(traceId);
    }

    public static String getTraceId() {
        return TRACE_ID.get();
    }

    public static void setTenantId(Long tenantId) {
        TENANT_ID.set(tenantId);
    }

    public static Long getTenantId() {
        return TENANT_ID.get();
    }

    public static void setUserId(Long userId) {
        USER_ID.set(userId);
    }

    public static Long getUserId() {
        return USER_ID.get();
    }

    public static void clear() {
        TRACE_ID.remove();
        TENANT_ID.remove();
        USER_ID.remove();
    }
}
