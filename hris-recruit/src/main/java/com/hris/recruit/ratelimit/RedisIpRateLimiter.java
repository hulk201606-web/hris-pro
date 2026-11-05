package com.hris.recruit.ratelimit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * 官网公开端点 Redis IP 限流器（批 7 #12）：多实例共享计数，替代原单机内存滑窗。
 *
 * <p>算法取舍：INCR + EXPIRE 固定窗口（窗口 60 秒 / 10 次）。相比滑窗，固定窗口在窗口边界
 * 存在最多 2 倍瞬时突刺（前窗末尾 + 后窗开头合计可达 20 次）；对官网防刷场景可接受，
 * 且实现原子、无状态、O(1)。若后续需要精确滑窗可改 ZSET 时间戳或 RedisCell，成本更高。</p>
 *
 * <p>降级语义（照 BlacklistGuard fail-open 风格）：Redis 不可用/超时 → log.warn 放行并如实报告。
 * 局限：降级期间限流完全失效（多实例攻击面放大），仅作防刷第一道闸而非安全边界。</p>
 */
@Component
public class RedisIpRateLimiter implements IpRateLimiter {

    private static final Logger log = LoggerFactory.getLogger(RedisIpRateLimiter.class);

    /** 固定窗口长度（毫秒）。 */
    static final long WINDOW_MILLIS = 60_000L;
    /** 窗口内允许的最大请求数。 */
    static final int MAX_REQUESTS = 10;

    static final String KEY_PREFIX = "rcu:ratelimit:apply:";

    private final StringRedisTemplate redis;

    public RedisIpRateLimiter(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public boolean tryAcquire(String key) {
        if (key == null || key.isBlank()) {
            // 无法识别来源时不限流（fail-open），避免误伤
            return true;
        }
        // 固定窗口 key：前缀 + IP + 窗口序号（epochMillis / window），窗口自然滚动
        String windowKey = KEY_PREFIX + key + ":" + (System.currentTimeMillis() / WINDOW_MILLIS);
        try {
            Long count = redis.opsForValue().increment(windowKey);
            if (count == null) {
                log.warn("限流计数返回空，fail-open 放行: key={}", key);
                return true;
            }
            if (count == 1L) {
                // 首次计数时设置过期，窗口结束后 key 自动清理
                redis.expire(windowKey, Duration.ofMillis(WINDOW_MILLIS));
            }
            return count <= MAX_REQUESTS;
        } catch (Exception e) {
            // Redis 不可用：fail-open 放行并告警（与 BlacklistGuard 降级风格一致）
            log.warn("限流 Redis 不可用，fail-open 放行: key={}, err={}", key, e.getMessage());
            return true;
        }
    }
}
