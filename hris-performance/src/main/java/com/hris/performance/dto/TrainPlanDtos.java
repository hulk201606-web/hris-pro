package com.hris.performance.dto;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 培训计划相关 DTO（域：绩效/培训，分层：DTO/接口契约）。培训计划创建/更新/查询的请求与响应结构。
 */
public final class TrainPlanDtos {

    private TrainPlanDtos() {
    }

    /** 创建请求。 */
    public record TrainPlanCreateRequest(@NotNull(message = "name 不能为空") String name, @NotNull(message = "code 不能为空") String code, LocalDate startDate, LocalDate endDate) {
    }

    /** 更新请求。 */
    public record TrainPlanUpdateRequest(String name, String code, LocalDate startDate, LocalDate endDate) {
    }

    /** 响应。 */
    public record TrainPlanResponse(Long id, String name, String code, LocalDate startDate, LocalDate endDate, LocalDateTime createdAt, LocalDateTime updatedAt) {
    }
}
