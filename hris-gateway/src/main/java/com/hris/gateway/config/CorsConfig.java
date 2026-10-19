package com.hris.gateway.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsWebFilter;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;

/**
 * 全局 CORS（WebFlux）。生产环境禁止 origin: "*"，只允许配置中显式列出的前端域名。
 */
@Configuration
public class CorsConfig {

    /**
     * 装配全局 CORS 过滤器（WebFlux 侧）：允许来源/方法/头从配置读取，
     * 生产环境禁止 {@code origin: "*"}，仅放行配置中显式列出的前端域名；
     * allowCredentials=true 时浏览器才允许携带 Cookie/Authorization 跨域。
     */
    @Bean
    public CorsWebFilter corsWebFilter(HrisGatewayProperties props) {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(props.getCors().getOrigins());
        config.setAllowedMethods(props.getCors().getMethods());
        config.setAllowedHeaders(props.getCors().getHeaders());
        config.setAllowCredentials(props.getCors().isAllowCredentials());
        config.setMaxAge(props.getCors().getMaxAgeSeconds());

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return new CorsWebFilter(source);
    }
}
