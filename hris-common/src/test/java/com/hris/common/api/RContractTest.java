package com.hris.common.api;

import com.hris.common.exception.ErrorCode;
import com.hris.common.trace.TraceContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * R 响应信封 + ErrorCode 契约测试（docs/07 §1：{code, message, data, traceId, timestamp}）。
 *
 * <p>11 个服务的控制器全部复用本信封，字段语义（0=成功、traceId 自动取自 TraceContext、
 * 时间戳为 epoch millis）由本测试锁定；ErrorCode 业务码唯一性防止误配冲突。</p>
 */
class RContractTest {

    @AfterEach
    void tearDown() {
        TraceContext.clear();
    }

    @Test
    @DisplayName("R.ok(data)：code=0、message=success、traceId 取自上下文、时间戳为正")
    void okEnvelope() {
        TraceContext.setTraceId("trace-xyz");
        R<String> r = R.ok("payload");

        assertEquals(0, r.getCode());
        assertEquals("success", r.getMessage());
        assertEquals("payload", r.getData());
        assertEquals("trace-xyz", r.getTraceId());
        assertTrue(r.getTimestamp() > 0, "时间戳应为 epoch millis 正数");
    }

    @Test
    @DisplayName("R.ok()：data 为 null 但信封完整")
    void okWithoutData() {
        R<Void> r = R.ok();
        assertEquals(0, r.getCode());
        assertNotNull(r.getTimestamp());
    }

    @Test
    @DisplayName("R.fail(ErrorCode)：业务码与消息取自错误码枚举")
    void failEnvelope() {
        R<Void> r = R.fail(ErrorCode.NOT_FOUND);
        assertEquals(ErrorCode.NOT_FOUND.getCode(), r.getCode());
        assertEquals(ErrorCode.NOT_FOUND.getMessage(), r.getMessage());
    }

    @Test
    @DisplayName("ErrorCode：业务码全局唯一（防止误配导致前端误判）")
    void errorCodesAreUnique() {
        Set<Integer> codes = new TreeSet<>();
        for (ErrorCode ec : ErrorCode.values()) {
            assertTrue(codes.add(ec.getCode()), "业务码重复: " + ec.getCode() + " (" + ec.name() + ")");
        }
    }

    @Test
    @DisplayName("ErrorCode：关键分段的码值符合 docs/07 约定（RAG=49000 等）")
    void errorCodeSegments() {
        assertEquals(49000, ErrorCode.RAG_ERROR.getCode());
        assertEquals(54001, ErrorCode.CMP_SALARY_FORBIDDEN.getCode());
    }
}
