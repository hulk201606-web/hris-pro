package com.hris.gateway.filter;

import com.hris.common.exception.ErrorCode;
import com.hris.gateway.common.GatewayConstants;
import com.hris.gateway.handler.GatewayErrorWriter;
import com.hris.gateway.jwt.GatewayPrincipal;
import com.hris.gateway.jwt.JwtTokenVerifier;
import io.jsonwebtoken.JwtException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * JWT 认证过滤器。解析 Authorization: Bearer，HS256 验签，成功后：
 * <ul>
 *   <li>透传 X-User-Id（sub）、X-Tenant-Id（tenant_id）、X-User-Roles（roles）、X-User-Perms（perms）到下游；</li>
 *   <li>丢弃客户端伪造的 X-Tenant-Id / X-User-Id / X-User-Roles / X-User-Perms（以 token 为准，防跨租户/越权探测）；</li>
 *   <li>主体写入交换属性 + Reactor Context，供日志与后续过滤器使用。</li>
 * </ul>
 */
@Component
public class JwtAuthGlobalFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthGlobalFilter.class);

    public static final int ORDER = -600;

    private final JwtTokenVerifier verifier;
    private final GatewayErrorWriter errorWriter;

    public JwtAuthGlobalFilter(JwtTokenVerifier verifier, GatewayErrorWriter errorWriter) {
        this.verifier = verifier;
        this.errorWriter = errorWriter;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        if (Boolean.TRUE.equals(exchange.getAttribute(GatewayConstants.ATTR_SKIP_AUTH))) {
            return chain.filter(exchange);
        }

        String authorization = exchange.getRequest().getHeaders().getFirst(GatewayConstants.HEADER_AUTHORIZATION);
        if (authorization == null || !authorization.startsWith(GatewayConstants.BEARER_PREFIX)) {
            return errorWriter.write(exchange, HttpStatus.UNAUTHORIZED, ErrorCode.UNAUTHORIZED);
        }

        String token = authorization.substring(GatewayConstants.BEARER_PREFIX.length()).trim();
        final GatewayPrincipal principal;
        try {
            principal = verifier.verifyAccess(token);
        } catch (JwtException | IllegalArgumentException e) {
            log.warn("jwt verify failed, path={}, reason={}",
                    exchange.getRequest().getPath().value(), e.getMessage());
            return errorWriter.write(exchange, HttpStatus.UNAUTHORIZED, ErrorCode.UNAUTHORIZED);
        }

        // 丢弃伪造头，以 token 为准重写 X-User-Id / X-Tenant-Id / X-User-Roles / X-User-Perms
        ServerHttpRequest mutated = exchange.getRequest().mutate()
                .headers(headers -> {
                    headers.remove(GatewayConstants.HEADER_TENANT_ID);
                    headers.remove(GatewayConstants.HEADER_USER_ID);
                    headers.remove(GatewayConstants.HEADER_ROLES);
                    headers.remove(GatewayConstants.HEADER_PERMS);
                    headers.set(GatewayConstants.HEADER_TENANT_ID, String.valueOf(principal.tenantId()));
                    headers.set(GatewayConstants.HEADER_USER_ID, String.valueOf(principal.userId()));
                    headers.set(GatewayConstants.HEADER_ROLES, String.join(",", principal.roles()));
                    headers.set(GatewayConstants.HEADER_PERMS, String.join(",", principal.perms()));
                })
                .build();

        ServerWebExchange mutatedExchange = exchange.mutate().request(mutated).build();
        mutatedExchange.getAttributes().put(GatewayConstants.ATTR_USER_ID, principal.userId());
        mutatedExchange.getAttributes().put(GatewayConstants.ATTR_TENANT_ID, principal.tenantId());

        return chain.filter(mutatedExchange)
                .contextWrite(ctx -> ctx
                        .put(GatewayConstants.ATTR_USER_ID, principal.userId())
                        .put(GatewayConstants.ATTR_TENANT_ID, principal.tenantId()));
    }

    @Override
    public int getOrder() {
        return ORDER;
    }
}
