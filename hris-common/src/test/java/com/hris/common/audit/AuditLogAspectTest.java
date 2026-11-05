package com.hris.common.audit;

import com.hris.common.trace.TraceContext;
import org.aspectj.lang.ProceedingJoinPoint;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * AuditLogAspect 单元测试（docs/19 §8.7-2）：
 * 有 Sink 同步落库、落库失败降级 logback 不阻断业务、无 Sink 保持 logback、
 * 业务异常标记 ok=false 且原样 rethrow。
 */
@ExtendWith(MockitoExtension.class)
class AuditLogAspectTest {

    @Mock
    private ObjectProvider<AuditSink> sinkProvider;
    @Mock
    private AuditSink sink;
    @Mock
    private ProceedingJoinPoint pjp;

    private AuditLogAspect aspect;

    @BeforeEach
    void setUp() {
        aspect = new AuditLogAspect(sinkProvider);
        TraceContext.setTenantId(42L);
        TraceContext.setUserId(7L);
        TraceContext.setTraceId("t-123");
    }

    @AfterEach
    void tearDown() {
        TraceContext.clear();
    }

    private AuditLog annotation() throws Exception {
        return AuditLogAspectTest.class
                .getDeclaredMethod("annotated").getAnnotation(AuditLog.class);
    }

    @AuditLog(value = "测试动作", module = "tst")
    void annotated() {
    }

    @Test
    @DisplayName("有 Sink：同步 append，记录 tenantId/userId/module/action")
    void appendsToSink() throws Throwable {
        when(pjp.proceed()).thenReturn("ok");
        when(sinkProvider.getIfAvailable()).thenReturn(sink);

        Object result = aspect.around(pjp, annotation());

        assertEquals("ok", result);
        ArgumentCaptor<AuditRecord> captor = ArgumentCaptor.forClass(AuditRecord.class);
        verify(sink).append(captor.capture());
        AuditRecord r = captor.getValue();
        assertEquals(42L, r.tenantId());
        assertEquals(7L, r.userId());
        assertEquals("tst", r.module());
        assertEquals("测试动作", r.action());
        assertTrue((Boolean) r.detail().get("ok"));
        assertEquals("t-123", r.detail().get("traceId"));
    }

    @Test
    @DisplayName("Sink 抛异常 → 降级不阻断业务，方法正常返回")
    void sinkFailureDoesNotBreakBusiness() throws Throwable {
        when(pjp.proceed()).thenReturn("ok");
        when(sinkProvider.getIfAvailable()).thenReturn(sink);
        doThrow(new RuntimeException("db down")).when(sink).append(any());

        Object result = aspect.around(pjp, annotation());

        assertEquals("ok", result, "落库失败不得影响业务返回");
    }

    @Test
    @DisplayName("无 Sink：不落库，业务正常（保持 logback 行为）")
    void noSinkKeepsLogback() throws Throwable {
        when(pjp.proceed()).thenReturn("ok");
        when(sinkProvider.getIfAvailable()).thenReturn(null);

        Object result = aspect.around(pjp, annotation());

        assertEquals("ok", result);
        verify(sink, never()).append(any());
    }

    @Test
    @DisplayName("业务异常：原样 rethrow，detail.ok=false")
    void businessExceptionMarkedNotOk() throws Throwable {
        when(pjp.proceed()).thenThrow(new RuntimeException("boom"));
        when(sinkProvider.getIfAvailable()).thenReturn(sink);

        RuntimeException thrown = assertThrows(RuntimeException.class,
                () -> aspect.around(pjp, annotation()));

        assertEquals("boom", thrown.getMessage());
        ArgumentCaptor<AuditRecord> captor = ArgumentCaptor.forClass(AuditRecord.class);
        verify(sink).append(captor.capture());
        assertFalse((Boolean) captor.getValue().detail().get("ok"));
    }
}
