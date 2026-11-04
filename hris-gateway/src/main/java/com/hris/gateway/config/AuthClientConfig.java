package com.hris.gateway.config;

import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * 网关直连内部服务的 WebClient 配置。@LoadBalanced 使 {@code lb://hris-auth} 经
 * 负载均衡解析到 auth 服务实例（不经网关自身路由，避开 InternalBanWebFilter 封禁）。
 */
@Configuration
public class AuthClientConfig {

    @Bean
    @LoadBalanced
    public WebClient.Builder loadBalancedWebClientBuilder() {
        return WebClient.builder();
    }
}
