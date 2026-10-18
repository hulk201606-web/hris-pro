package com.hris.auth.config;

import com.hris.auth.model.LoginUser;

/**
 * 认证上下文（ThreadLocal）。由 {@link JwtAuthenticationFilter} 写入、finally 清理。
 * 服务层经此读取当前用户，不直接依赖 HttpServletRequest。
 */
public final class AuthContext {

    private static final ThreadLocal<LoginUser> CURRENT = new ThreadLocal<>();

    private AuthContext() {
    }

    public static void set(LoginUser user) {
        CURRENT.set(user);
    }

    public static LoginUser get() {
        return CURRENT.get();
    }

    public static void clear() {
        CURRENT.remove();
    }
}
