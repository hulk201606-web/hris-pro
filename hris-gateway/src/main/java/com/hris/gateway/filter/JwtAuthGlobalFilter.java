package com.hris.gateway.filter;

import com.hris.common.exception.ErrorCode;
import com.hris.gateway.auth.PermQueryService;
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
 *   <li>透传 X-User-Id（sub）、X-Tenant-Id（tenant_id）到下游；</li>
 *   <li>经 PermQueryService 实时查询 perms 后透传 X-User-Perms（roles 不进 token，也不再透传）；</li>
 *   <li>丢弃客户端伪造的 X-Tenant-Id / X-User-Id / X-User-Roles / X-User-Perms（防跨租户/越权探测）；</li>
 *   <li>主体写入交换属性 + Reactor Context，供日志与后续过滤器使用。</li>
 * </ul>
 */
@Component
public class JwtAuthGlobalFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthGlobalFilter.class);

    public static final int ORDER = -600;

    private final JwtTokenVerifier verifier;
    private final PermQueryService permQueryService;
    private final GatewayErrorWriter errorWriter;

    public JwtAuthGlobalFilter(JwtTokenVerifier verifier, PermQueryService permQueryService,
                               GatewayErrorWriter errorWriter) {
        this.verifier = verifier;
        this.permQueryService = permQueryService;
        this.errorWriter = errorWriter;
    }

    /**
     * JWT 认证过滤器（ORDER=-600）：
     * <ol>
     *   <li>白名单已标记 ATTR_SKIP_AUTH=true 时直接放行（登录/刷新/健康检查）；</li>
     *   <li>无 Authorization Bearer 头 → 401 UNAUTHORIZED；</li>
     *   <li>调用 JwtTokenVerifier.verifyAccess 做 HS256 验签 + issuer/类型校验，失败 → 401；</li>
     *   <li>丢弃客户端伪造的 X-Tenant-Id/X-User-Id/X-User-Roles/X-User-Perms 头，
     *       以 token 中 claims 为准重写身份头，perms 经 PermQueryService 实时查询后写头；</li>
     *   <li>主体写入交换属性与 Reactor Context，供 AccessLog 与下游透传。</li>
     * </ol>
     */
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

        // 验签通过后实时查权限，再丢弃伪造头、以 token/查询结果为准重写身份与权限头
        return permQueryService.permsOf(principal.userId(), principal.tenantId())
                .flatMap(perms -> {
                    ServerHttpRequest mutated = exchange.getRequest().mutate()
                            .headers(headers -> {
                                headers.remove(GatewayConstants.HEADER_TENANT_ID);
                                headers.remove(GatewayConstants.HEADER_USER_ID);
                                headers.remove(GatewayConstants.HEADER_ROLES);
                                headers.remove(GatewayConstants.HEADER_PERMS);
                                headers.set(GatewayConstants.HEADER_TENANT_ID, String.valueOf(principal.tenantId()));
                                headers.set(GatewayConstants.HEADER_USER_ID, String.valueOf(principal.userId()));
                                headers.set(GatewayConstants.HEADER_PERMS, String.join(",", perms));
                            })
                            .build();

                    ServerWebExchange mutatedExchange = exchange.mutate().request(mutated).build();
                    mutatedExchange.getAttributes().put(GatewayConstants.ATTR_USER_ID, principal.userId());
                    mutatedExchange.getAttributes().put(GatewayConstants.ATTR_TENANT_ID, principal.tenantId());

                    return chain.filter(mutatedExchange)
                            .contextWrite(ctx -> ctx
                                    .put(GatewayConstants.ATTR_USER_ID, principal.userId())
                                    .put(GatewayConstants.ATTR_TENANT_ID, principal.tenantId()));
                });
    }

    @Override
    public int getOrder() {
        return ORDER;
    }
}
