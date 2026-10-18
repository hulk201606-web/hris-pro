package com.hris.gateway.filter;

import com.hris.gateway.common.GatewayConstants;
import com.hris.gateway.config.HrisGatewayProperties;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * 白名单过滤器。命中白名单（登录/刷新/公开资源/健康检查等）则标记跳过 JWT 校验，
 * 由后续 JwtAuthGlobalFilter 读取该标记决定是否放行。
 */
@Component
public class WhitelistGlobalFilter implements GlobalFilter, Ordered {

    public static final int ORDER = -800;

    private final HrisGatewayProperties props;
    private final AntPathMatcher matcher = new AntPathMatcher();

    public WhitelistGlobalFilter(HrisGatewayProperties props) {
        this.props = props;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getURI().getPath();
        boolean whitelisted = props.getWhitelist().stream()
                .anyMatch(pattern -> matcher.match(pattern, path));
        if (whitelisted) {
            exchange.getAttributes().put(GatewayConstants.ATTR_SKIP_AUTH, Boolean.TRUE);
        }
        return chain.filter(exchange);
    }

    @Override
    public int getOrder() {
        return ORDER;
    }
}
