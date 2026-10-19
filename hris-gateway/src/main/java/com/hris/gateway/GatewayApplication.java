package com.hris.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 智云 HR 多租户 SaaS 网关服务启动类（hris-gateway 业务域 · 启动入口）。
 *
 * <p>基于 Spring Cloud Gateway（WebFlux 响应式），承担：路由转发、JWT 验签、
 * 白名单放行、内部端点封禁、请求体大小守卫、Sentinel 限流、traceId 透传、
 * 统一错误响应写出。scanBasePackages 指向 com.hris 根包，以同时扫描
 * hris-common 公共组件（R / ErrorCode / JwtSecretGuard 等）与 gateway 自身。</p>
 *
 * <p>注意：网关是 WebFlux 应用，不加载 hris-common 的 Servlet 侧
 * {@code @RestControllerAdvice}（已由 {@code @ConditionalOnWebApplication(SERVLET)} 排除）。</p>
 */
@SpringBootApplication(scanBasePackages = "com.hris")
public class GatewayApplication {

    /**
     * 网关服务入口。
     *
     * @param args 启动参数（由 Spring Boot 解析，如 --server.port=8080）
     */
    public static void main(String[] args) {
        SpringApplication.run(GatewayApplication.class, args);
    }
}
