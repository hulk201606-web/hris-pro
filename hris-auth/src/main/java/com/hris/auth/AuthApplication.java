package com.hris.auth;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 智云 HR 认证授权服务启动类（hris-auth 业务域 · 启动入口）。
 *
 * <p>承担：账号密码登录、JWT access/refresh 令牌签发与刷新、登出撤销、
 * 当前用户/菜单/权限点查询、MFA（TOTP）绑定校验、内部入职建号/离职禁用。
 * 是多租户 SaaS 的身份提供者（IdP），与 hris-gateway 共享同一 JWT HS256 密钥。</p>
 *
 * <p>scanBasePackages 指向 com.hris 根包，以同时扫描 hris-common 公共组件
 * （R / BizException / TraceContext / MyBatis-Plus 租户插件 / AuditLog 切面等）与 auth 自身。</p>
 */
@SpringBootApplication(scanBasePackages = "com.hris")
public class AuthApplication {

    /**
     * 认证服务入口。
     *
     * @param args 启动参数（由 Spring Boot 解析）
     */
    public static void main(String[] args) {
        SpringApplication.run(AuthApplication.class, args);
    }
}
