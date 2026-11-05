package com.hris.auth.store;

import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;

/**
 * Redis 版 TokenStore（StringRedisTemplate），多实例部署用。
 *
 * <p>键设计：refresh = {@code auth:refresh:{jti}}，值 = 过期 epoch 毫秒，
 * TTL = exp - now；黑名单 = {@code auth:bl:{jti}}，TTL = 剩余有效期。
 * 两个键到期均由 Redis 自动清除，无需后台清扫。</p>
 *
 * <p>TTL 以秒向上取整，避免毫秒余量被截断后键提前消失的边界问题。</p>
 */
public class RedisTokenStore implements TokenStore {

    static final String REFRESH_KEY_PREFIX = "auth:refresh:";
    static final String BLACKLIST_KEY_PREFIX = "auth:bl:";

    private final StringRedisTemplate redis;

    public RedisTokenStore(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public void putRefresh(String jti, long expEpochMs) {
        long ttlMs = expEpochMs - System.currentTimeMillis();
        if (ttlMs <= 0) {
            return;
        }
        redis.opsForValue().set(REFRESH_KEY_PREFIX + jti, String.valueOf(expEpochMs),
                Duration.ofSeconds(ceilSeconds(ttlMs)));
    }

    @Override
    public boolean existsRefresh(String jti) {
        return Boolean.TRUE.equals(redis.hasKey(REFRESH_KEY_PREFIX + jti));
    }

    @Override
    public void removeRefresh(String jti) {
        redis.delete(REFRESH_KEY_PREFIX + jti);
    }

    @Override
    public void blacklist(String jti, long ttlMs) {
        if (ttlMs <= 0) {
            return;
        }
        redis.opsForValue().set(BLACKLIST_KEY_PREFIX + jti, "1",
                Duration.ofSeconds(ceilSeconds(ttlMs)));
    }

    @Override
    public boolean isBlacklisted(String jti) {
        return Boolean.TRUE.equals(redis.hasKey(BLACKLIST_KEY_PREFIX + jti));
    }

    /** 毫秒 TTL 向上取整为秒（Redis SET EX 精度），恒 >= 1。 */
    static long ceilSeconds(long ttlMs) {
        return Math.max(1, (ttlMs + 999) / 1000);
    }
}
