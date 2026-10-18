package com.hris.compensation.dto;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;

/**
 * PayGroup DTO（由 DDL 自动生成）。
 */
public final class PayGroupDtos {

    private PayGroupDtos() {
    }

    /** 创建请求。 */
    public record PayGroupCreateRequest(@NotNull(message = "name 不能为空") String name, @NotNull(message = "code 不能为空") String code, @NotNull(message = "payCycle 不能为空") String payCycle) {
    }

    /** 更新请求。 */
    public record PayGroupUpdateRequest(String name, String code, String payCycle) {
    }

    /** 响应。 */
    public record PayGroupResponse(Long id, String name, String code, String payCycle, LocalDateTime createdAt, LocalDateTime updatedAt) {
    }
}
