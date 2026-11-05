package com.hris.recruit.ratelimit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * RedisIpRateLimiter 单元测试（批 7 #12）：固定窗口计数 / 超限拒绝 / blank key 放行 /
 * Redis 不可用 fail-open 降级。
 */
@ExtendWith(MockitoExtension.class)
class RedisIpRateLimiterTest {

    private static final String IP = "10.0.0.1";

    @Mock
    private StringRedisTemplate redis;
    @Mock
    private ValueOperations<String, String> valueOps;

    private RedisIpRateLimiter limiter;

    @BeforeEach
    void setUp() {
        limiter = new RedisIpRateLimiter(redis);
        lenient().when(redis.opsForValue()).thenReturn(valueOps);
    }

    @Test
    @DisplayName("窗口内计数递增，未超限放行；首次计数设置 EXPIRE")
    void incrementsAndAllowsWithinLimit() {
        when(valueOps.increment(contains(IP))).thenReturn(1L, 2L, 3L);

        assertTrue(limiter.tryAcquire(IP));
        assertTrue(limiter.tryAcquire(IP));
        assertTrue(limiter.tryAcquire(IP));
        verify(redis).expire(contains(IP), eq(Duration.ofMillis(RedisIpRateLimiter.WINDOW_MILLIS)));
    }

    @Test
    @DisplayName("窗口内第 11 次请求触发限流")
    void blocksEleventhRequest() {
        when(valueOps.increment(contains(IP)))
                .thenReturn((long) RedisIpRateLimiter.MAX_REQUESTS,
                        (long) RedisIpRateLimiter.MAX_REQUESTS + 1);

        assertTrue(limiter.tryAcquire(IP), "第 10 次应放行");
        assertFalse(limiter.tryAcquire(IP), "第 11 次应被限流");
    }

    @Test
    @DisplayName("blank key fail-open 放行，不触达 Redis")
    void allowsBlankKey() {
        assertTrue(limiter.tryAcquire(" "));
        assertTrue(limiter.tryAcquire(null));
        verify(valueOps, never()).increment(anyString());
    }

    @Test
    @DisplayName("Redis 连接异常 → fail-open 放行（降级语义）")
    void failsOpenOnRedisError() {
        when(valueOps.increment(anyString())).thenThrow(new RedisConnectionFailureException("conn refused"));
        assertTrue(limiter.tryAcquire(IP), "Redis 不可用应放行");
    }

    @Test
    @DisplayName("Redis 计数返回 null（管道异常等）→ fail-open 放行")
    void failsOpenOnNullCount() {
        when(valueOps.increment(anyString())).thenReturn(null);
        assertTrue(limiter.tryAcquire(IP));
    }
}
