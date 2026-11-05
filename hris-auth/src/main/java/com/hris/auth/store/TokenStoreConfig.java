package com.hris.auth.store;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * TokenStore 装配：按 {@code hris.auth.token-store} 选择实现。
 *
 * <p>缺省 memory（单测/单机零外部依赖）；redis 模式仅在显式配置时加载
 * RedisTokenStore，且要求 classpath 上有 spring-boot-starter-data-redis
 * 自动装配的 StringRedisTemplate。</p>
 */
@Configuration(proxyBeanMethods = false)
public class TokenStoreConfig {

    @Bean
    @ConditionalOnProperty(name = "hris.auth.token-store", havingValue = "memory", matchIfMissing = true)
    public TokenStore inMemoryTokenStore() {
        return new InMemoryTokenStore();
    }

    @Bean
    @ConditionalOnProperty(name = "hris.auth.token-store", havingValue = "redis")
    public TokenStore redisTokenStore(StringRedisTemplate stringRedisTemplate) {
        return new RedisTokenStore(stringRedisTemplate);
    }
}
