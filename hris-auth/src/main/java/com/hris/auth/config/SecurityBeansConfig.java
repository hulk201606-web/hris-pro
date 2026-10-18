package com.hris.auth.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * 安全相关 Bean 装配。密码哈希 Argon2id（docs/08 §1），
 * 默认参数：salt 16 字节 / hash 32 字节 / 并行度 1 / 内存 64MB / 迭代 3。
 */
@Configuration
@EnableConfigurationProperties(AuthProperties.class)
public class SecurityBeansConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8();
    }
}
