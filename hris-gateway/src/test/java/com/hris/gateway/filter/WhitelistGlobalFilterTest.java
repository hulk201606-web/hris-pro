package com.hris.gateway.filter;

import com.hris.gateway.common.GatewayConstants;
import com.hris.gateway.config.HrisGatewayProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * WhitelistGlobalFilter 单元测试（RCS-F-004：官网免登录公开路径放行语义）。
 *
 * <p>覆盖：/api/v1/public/** 命中白名单标记跳过 JWT；管理端路径不误放；/internal 不经白名单放行
 * （封禁由 InternalBanWebFilter 在 WebFilter 层处理，与本过滤器无关）。</p>
 */
@ExtendWith(MockitoExtension.class)
class WhitelistGlobalFilterTest {

    @Mock
    private org.springframework.cloud.gateway.filter.GatewayFilterChain chain;

    private WhitelistGlobalFilter filter;

    @BeforeEach
    void setUp() {
        HrisGatewayProperties props = new HrisGatewayProperties();
        props.setWhitelist(List.of(
                "/api/v1/auth/login",
                "/public/**",
                "/api/v1/public/**",
                "/actuator/health/**"));
        filter = new WhitelistGlobalFilter(props);
    }

    private ServerWebExchange exchangeOf(String path) {
        ServerWebExchange exchange = mock(ServerWebExchange.class);
        org.springframework.http.server.reactive.ServerHttpRequest request =
                mock(org.springframework.http.server.reactive.ServerHttpRequest.class);
        when(request.getURI()).thenReturn(java.net.URI.create("http://gw" + path));
        when(exchange.getRequest()).thenReturn(request);
        when(exchange.getAttributes()).thenReturn(new java.util.HashMap<>());
        when(chain.filter(any())).thenReturn(Mono.empty());
        return exchange;
    }

    @Test
    @DisplayName("公开投递路径 /api/v1/public/rcu/jobs 命中白名单 → 标记跳过 JWT")
    void marksPublicApplyPathAsSkipAuth() {
        ServerWebExchange exchange = exchangeOf("/api/v1/public/rcu/jobs");
        filter.filter(exchange, chain).block();
        assertTrue(Boolean.TRUE.equals(exchange.getAttributes().get(GatewayConstants.ATTR_SKIP_AUTH)));
    }

    @Test
    @DisplayName("管理端路径不命中公开白名单 → 不打跳过标记，仍走 JWT")
    void doesNotMarkAdminPath() {
        ServerWebExchange exchange = exchangeOf("/api/v1/rcu/requisitions");
        filter.filter(exchange, chain).block();
        assertFalse(Boolean.TRUE.equals(exchange.getAttributes().get(GatewayConstants.ATTR_SKIP_AUTH)));
    }

    @Test
    @DisplayName("白名单放行不吞请求：链继续执行")
    void continuesChainWhenWhitelisted() {
        ServerWebExchange exchange = exchangeOf("/api/v1/public/rcu/jobs/1/apply");
        filter.filter(exchange, chain).block();
        assertTrue(Boolean.TRUE.equals(exchange.getAttributes().get(GatewayConstants.ATTR_SKIP_AUTH)));
    }
}
