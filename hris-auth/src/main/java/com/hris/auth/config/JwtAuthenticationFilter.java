package com.hris.auth.config;

import com.hris.auth.model.LoginUser;
import com.hris.auth.service.TokenService;
import com.hris.common.exception.BizException;
import com.hris.common.trace.TraceContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * JWT 认证过滤器：解析 Authorization: Bearer 令牌，写入 TraceContext（供租户插件）与
 * AuthContext（供服务层读当前用户）。令牌缺失/无效时静默放行，受保护端点由 controller
 * 经 AuthContext 判空后返回 401。
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final TokenService tokenService;

    public JwtAuthenticationFilter(TokenService tokenService) {
        this.tokenService = tokenService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith(BEARER_PREFIX)) {
            try {
                LoginUser user = tokenService.parseAccess(header.substring(BEARER_PREFIX.length()));
                TraceContext.setTenantId(user.tenantId());
                TraceContext.setUserId(user.userId());
                AuthContext.set(user);
            } catch (BizException ignore) {
                // 令牌无效：受保护端点将因 AuthContext 为空返回 401
            }
        }
        try {
            filterChain.doFilter(request, response);
        } finally {
            AuthContext.clear();
        }
    }
}
