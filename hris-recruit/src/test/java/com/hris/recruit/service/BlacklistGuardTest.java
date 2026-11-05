package com.hris.recruit.service;

import com.hris.common.exception.BizException;
import com.hris.common.exception.ErrorCode;
import com.hris.recruit.client.EmpCheckClient;
import com.hris.recruit.config.RecruitProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

/**
 * BlacklistGuard 单元测试（批 7 #14）：fail-open / fail-closed 两态降级语义 + 命中拒绝。
 */
@ExtendWith(MockitoExtension.class)
class BlacklistGuardTest {

    private static final String MOBILE = "13812348888";

    @Mock
    private EmpCheckClient empCheckClient;

    private RecruitProperties properties;

    @BeforeEach
    void setUp() {
        properties = new RecruitProperties();
    }

    private BlacklistGuard guard() {
        return new BlacklistGuard(empCheckClient, properties);
    }

    private com.hris.common.api.R<Map<String, Object>> resp(boolean success, Map<String, Object> data) {
        com.hris.common.api.R<Map<String, Object>> r = success
                ? com.hris.common.api.R.ok(data)
                : com.hris.common.api.R.fail(ErrorCode.RCS_ERROR);
        return r;
    }

    @Nested
    @DisplayName("fail-open=true（缺省，保持现状）")
    class FailOpen {

        @Test
        @DisplayName("emp 超时/异常 → 放行")
        void passesOnException() {
            properties.getBlacklist().setFailOpen(true);
            when(empCheckClient.blacklistCheck(null, MOBILE)).thenThrow(new RuntimeException("timeout"));
            assertDoesNotThrow(() -> guard().checkOrThrow(null, MOBILE));
        }

        @Test
        @DisplayName("emp 业务失败（success=false）→ 放行")
        void passesOnBusinessFailure() {
            properties.getBlacklist().setFailOpen(true);
            when(empCheckClient.blacklistCheck(null, MOBILE)).thenReturn(resp(false, null));
            assertDoesNotThrow(() -> guard().checkOrThrow(null, MOBILE));
        }

        @Test
        @DisplayName("命中黑名单 → PARAM_INVALID 黑名单文案（两态一致）")
        void rejectsBlacklisted() {
            properties.getBlacklist().setFailOpen(true);
            when(empCheckClient.blacklistCheck(null, MOBILE))
                    .thenReturn(resp(true, Map.of("blacklisted", true, "reason", "fraud")));
            BizException e = assertThrows(BizException.class, () -> guard().checkOrThrow(null, MOBILE));
            assertEquals(ErrorCode.PARAM_INVALID.getCode(), e.getCode());
            assertEquals(BlacklistGuard.HIT_MESSAGE, e.getMessage());
        }
    }

    @Nested
    @DisplayName("fail-open=false（fail-closed）")
    class FailClosed {

        @Test
        @DisplayName("emp 超时/异常 → PARAM_INVALID「系统繁忙请稍后再试」，不透露内部原因")
        void rejectsOnException() {
            properties.getBlacklist().setFailOpen(false);
            when(empCheckClient.blacklistCheck(null, MOBILE))
                    .thenThrow(new RuntimeException("connect timeout"));
            BizException e = assertThrows(BizException.class, () -> guard().checkOrThrow(null, MOBILE));
            assertEquals(ErrorCode.PARAM_INVALID.getCode(), e.getCode());
            assertEquals(BlacklistGuard.BUSY_MESSAGE, e.getMessage());
        }

        @Test
        @DisplayName("emp 业务失败 → 同样拒绝")
        void rejectsOnBusinessFailure() {
            properties.getBlacklist().setFailOpen(false);
            when(empCheckClient.blacklistCheck(null, MOBILE)).thenReturn(resp(false, null));
            BizException e = assertThrows(BizException.class, () -> guard().checkOrThrow(null, MOBILE));
            assertEquals(BlacklistGuard.BUSY_MESSAGE, e.getMessage());
        }

        @Test
        @DisplayName("emp 正常返回未命中 → 放行")
        void passesWhenNotBlacklisted() {
            properties.getBlacklist().setFailOpen(false);
            when(empCheckClient.blacklistCheck(null, MOBILE))
                    .thenReturn(resp(true, Map.of("blacklisted", false)));
            assertDoesNotThrow(() -> guard().checkOrThrow(null, MOBILE));
        }

        @Test
        @DisplayName("命中黑名单 → 黑名单文案（与 fail-open 一致）")
        void rejectsBlacklisted() {
            properties.getBlacklist().setFailOpen(false);
            when(empCheckClient.blacklistCheck(null, MOBILE))
                    .thenReturn(resp(true, Map.of("blacklisted", true)));
            BizException e = assertThrows(BizException.class, () -> guard().checkOrThrow(null, MOBILE));
            assertEquals(BlacklistGuard.HIT_MESSAGE, e.getMessage());
        }
    }

    @Test
    @DisplayName("mobile 为空 → 两态均直接放行（无手机号无黑名单维度）")
    void passesOnBlankMobile() {
        properties.getBlacklist().setFailOpen(false);
        assertDoesNotThrow(() -> guard().checkOrThrow(null, " "));
    }
}
