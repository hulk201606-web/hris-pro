package com.hris.recruit.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;

/**
 * SiteDomain DTO（批 7 #13）：官网租户域名映射。
 */
public final class SiteDomainDtos {

    private SiteDomainDtos() {
    }

    /** 创建请求：域名小写、不含协议与端口（服务端再归一化）。 */
    public record SiteDomainCreateRequest(
            @NotBlank(message = "domain 不能为空")
            @Size(max = 128, message = "domain 长度不能超过 128")
            @Pattern(regexp = "^[A-Za-z0-9.-]+", message = "domain 仅支持字母/数字/点/连字符")
            String domain,
            String status) {
    }

    /** 更新请求：未传字段不更新。 */
    public record SiteDomainUpdateRequest(
            @Size(max = 128, message = "domain 长度不能超过 128")
            @Pattern(regexp = "^[A-Za-z0-9.-]+$", message = "domain 仅支持字母/数字/点/连字符")
            String domain,
            String status) {
    }

    /** 响应。 */
    public record SiteDomainResponse(Long id, Long tenantId, String domain, String status,
                                     LocalDateTime createdAt, LocalDateTime updatedAt) {
    }
}
