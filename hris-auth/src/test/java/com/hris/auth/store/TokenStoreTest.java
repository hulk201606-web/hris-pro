package com.hris.auth.store;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * TokenStore 语义单测（docs/16 L1，不依赖真实 Redis）。
 *
 * <p>memory 实现真实跑语义；redis 实现用 mock StringRedisTemplate 锁定
 * 键名（auth:refresh: / auth:bl:）、TTL（秒向上取整）与删除契约——
 * 键名/TTL 是跨实例与运维排查的稳定契约，不可静默漂移。</p>
 */
@DisplayName("TokenStore：内存语义与 Redis 键名/TTL 契约")
class TokenStoreTest {

    // ==================== memory 实现真实语义 ====================
    @Nested
    @DisplayName("InMemoryTokenStore")
    class Memory {

        private InMemoryTokenStore store;

        @BeforeEach
        void setUp() {
            store = new InMemoryTokenStore();
        }

        @Test
        @DisplayName("putRefresh 后 exists，removeRefresh 后不存在")
        void refreshLifecycle() {
            long exp = System.currentTimeMillis() + 60_000;

            store.putRefresh("jti-1", exp);
            assertTrue(store.existsRefresh("jti-1"));

            store.removeRefresh("jti-1");
            assertFalse(store.existsRefresh("jti-1"));
        }

        @Test
        @DisplayName("removeRefresh 幂等，不存在的 jti 不抛异常")
        void removeIsIdempotent() {
            store.removeRefresh("not-exist");
            assertFalse(store.existsRefresh("not-exist"));
        }

        @Test
        @DisplayName("blacklist 后 isBlacklisted 命中")
        void blacklistLifecycle() {
            store.blacklist("jti-b", 60_000);
            assertTrue(store.isBlacklisted("jti-b"));
        }

        @Test
        @DisplayName("ttlMs <= 0 不登记黑名单（已过期令牌无需撤销）")
        void blacklistSkipsNonPositiveTtl() {
            store.blacklist("jti-expired", 0);
            store.blacklist("jti-expired", -1000);
            assertFalse(store.isBlacklisted("jti-expired"));
        }
    }

    // ==================== redis 实现契约（mock，不连真实 Redis） ====================
    @Nested
    @DisplayName("RedisTokenStore")
    class Redis {

        private StringRedisTemplate redis;
        private ValueOperations<String, String> valueOps;
        private RedisTokenStore store;

        @BeforeEach
        @SuppressWarnings("unchecked")
        void setUp() {
            redis = mock(StringRedisTemplate.class);
            valueOps = mock(ValueOperations.class);
            when(redis.opsForValue()).thenReturn(valueOps);
            store = new RedisTokenStore(redis);
        }

        @Test
        @DisplayName("putRefresh：键 auth:refresh:{jti}，值=exp 毫秒，TTL=60s（秒向上取整）")
        void putRefreshWritesKeyWithTtl() {
            long exp = System.currentTimeMillis() + 60_000;

            store.putRefresh("jti-r", exp);

            verify(valueOps).set("auth:refresh:jti-r", String.valueOf(exp), Duration.ofSeconds(60));
        }

        @Test
        @DisplayName("putRefresh：毫秒余量向上取整（1500ms → 2s，不截断丢余量）")
        void putRefreshCeilsTtl() {
            long exp = System.currentTimeMillis() + 60_500;

            store.putRefresh("jti-r", exp);

            verify(valueOps).set(eq("auth:refresh:jti-r"), eq(String.valueOf(exp)),
                    eq(Duration.ofSeconds(61)));
        }

        @Test
        @DisplayName("putRefresh：已过期（exp-now <= 0）不写入")
        void putRefreshSkipsExpired() {
            long exp = System.currentTimeMillis() - 1000;

            store.putRefresh("jti-r", exp);

            verify(valueOps, never()).set(anyString(), anyString(), org.mockito.ArgumentMatchers.any(Duration.class));
        }

        @Test
        @DisplayName("existsRefresh：hasKey(auth:refresh:{jti})，null 视为 false")
        void existsRefreshChecksKey() {
            when(redis.hasKey("auth:refresh:jti-1")).thenReturn(true);
            when(redis.hasKey("auth:refresh:jti-2")).thenReturn(null);

            assertTrue(store.existsRefresh("jti-1"));
            assertFalse(store.existsRefresh("jti-2"));
        }

        @Test
        @DisplayName("removeRefresh：删除 auth:refresh:{jti}")
        void removeRefreshDeletesKey() {
            store.removeRefresh("jti-r");

            verify(redis).delete("auth:refresh:jti-r");
        }

        @Test
        @DisplayName("blacklist：键 auth:bl:{jti}，TTL=剩余毫秒向上取整（1500ms → 2s）")
        void blacklistWritesKeyWithTtl() {
            store.blacklist("jti-x", 1500);

            verify(valueOps).set("auth:bl:jti-x", "1", Duration.ofSeconds(2));
        }

        @Test
        @DisplayName("blacklist：ttlMs <= 0 不写入")
        void blacklistSkipsNonPositiveTtl() {
            store.blacklist("jti-x", 0);
            store.blacklist("jti-x", -500);

            verify(valueOps, never()).set(anyString(), anyString(), org.mockito.ArgumentMatchers.any(Duration.class));
        }

        @Test
        @DisplayName("isBlacklisted：hasKey(auth:bl:{jti})")
        void isBlacklistedChecksKey() {
            when(redis.hasKey("auth:bl:jti-x")).thenReturn(true);

            assertTrue(store.isBlacklisted("jti-x"));
            verify(redis).hasKey("auth:bl:jti-x");
        }

        @Test
        @DisplayName("ceilSeconds 边界：999ms→1s / 1000ms→1s / 1001ms→2s")
        void ceilSecondsBoundaries() {
            assertEquals(1, RedisTokenStore.ceilSeconds(999));
            assertEquals(1, RedisTokenStore.ceilSeconds(1000));
            assertEquals(2, RedisTokenStore.ceilSeconds(1001));
            assertEquals(1, RedisTokenStore.ceilSeconds(0));
        }
    }
}
