package com.hris.gateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 网关查权限配置：auth 内部端点路径与本地缓存 TTL。
 */
@Component
@ConfigurationProperties(prefix = "hris.gateway.auth")
public class AuthClientProperties {

    /** auth 内部权限端点路径（含 {userId} 占位符）。 */
    private String permsPath = "/internal/auth/users/{userId}/perms";

    /** 权限本地缓存 TTL（秒），默认 5 分钟。 */
    private long cacheTtlSeconds = 300;

    public String getPermsPath() {
        return permsPath;
    }

    public void setPermsPath(String permsPath) {
        this.permsPath = permsPath;
    }

    public long getCacheTtlSeconds() {
        return cacheTtlSeconds;
    }

    public void setCacheTtlSeconds(long cacheTtlSeconds) {
        this.cacheTtlSeconds = cacheTtlSeconds;
    }
}
