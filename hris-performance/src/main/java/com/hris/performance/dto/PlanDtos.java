package com.hris.performance.dto;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;

/**
 * Plan DTO（由 DDL 自动生成）。
 */
public final class PlanDtos {

    private PlanDtos() {
    }

    /** 创建请求。 */
    public record PlanCreateRequest(@NotNull(message = "name 不能为空") String name, @NotNull(message = "code 不能为空") String code, @NotNull(message = "status 不能为空") Integer status) {
    }

    /** 更新请求。 */
    public record PlanUpdateRequest(String name, String code, Integer status) {
    }

    /** 响应。 */
    public record PlanResponse(Long id, String name, String code, Integer status, LocalDateTime createdAt, LocalDateTime updatedAt) {
    }
}
