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
