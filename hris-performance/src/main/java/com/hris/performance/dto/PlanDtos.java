package com.hris.performance.dto;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;

/**
 * 绩效计划相关 DTO（域：绩效，分层：DTO/接口契约）。计划模板创建/更新/查询的请求与响应结构。
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
