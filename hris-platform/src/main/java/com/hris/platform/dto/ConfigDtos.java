package com.hris.platform.dto;

import java.time.LocalDateTime;

/**
 * 系统配置中心 DTO（PLT-F-010）。
 */
public final class ConfigDtos {

    private ConfigDtos() {
    }

    /** 租户参数设置请求（key 走路径，body 只传值） */
    public record ConfigRequest(String configValue) {
    }

    /** 租户参数响应 */
    public record ConfigResponse(
            Long id,
            String configKey,
            String configValue,
            LocalDateTime updatedAt) {
    }
}
