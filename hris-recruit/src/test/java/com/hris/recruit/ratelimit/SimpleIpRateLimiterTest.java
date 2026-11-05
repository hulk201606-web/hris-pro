package com.hris.recruit.ratelimit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * SimpleIpRateLimiter 单元测试：60 秒窗口 10 次/IP，超出触发限流；blank key fail-open。
 */
class SimpleIpRateLimiterTest {

    @Test
    @DisplayName("窗口内第 11 次请求触发限流")
    void blocksEleventhRequestInWindow() {
        SimpleIpRateLimiter limiter = new SimpleIpRateLimiter();
        for (int i = 0; i < SimpleIpRateLimiter.MAX_REQUESTS; i++) {
            assertTrue(limiter.tryAcquire("10.0.0.1"), "前 10 次应放行");
        }
        assertFalse(limiter.tryAcquire("10.0.0.1"), "第 11 次应被限流");
    }

    @Test
    @DisplayName("不同 IP 互不影响")
    void isolatesByIp() {
        SimpleIpRateLimiter limiter = new SimpleIpRateLimiter();
        for (int i = 0; i < SimpleIpRateLimiter.MAX_REQUESTS; i++) {
            limiter.tryAcquire("10.0.0.2");
        }
        assertTrue(limiter.tryAcquire("10.0.0.3"));
    }

    @Test
    @DisplayName("blank key fail-open 放行")
    void allowsBlankKey() {
        SimpleIpRateLimiter limiter = new SimpleIpRateLimiter();
        assertTrue(limiter.tryAcquire(" "));
        assertTrue(limiter.tryAcquire(null));
    }
}
