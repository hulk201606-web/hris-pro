package com.hris.performance.dto;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * TrainPlan DTO（由 DDL 自动生成）。
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
