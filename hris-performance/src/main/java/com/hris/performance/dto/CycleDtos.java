package com.hris.performance.dto;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 绩效周期相关 DTO（域：绩效，分层：DTO/接口契约）。周期创建/更新/响应结构，含状态机字段。
 */
public final class CycleDtos {

    private CycleDtos() {
    }

    /** 创建请求。 */
    public record CycleCreateRequest(@NotNull(message = "planId 不能为空") Long planId, @NotNull(message = "periodCode 不能为空") String periodCode, @NotNull(message = "startDate 不能为空") LocalDate startDate, @NotNull(message = "endDate 不能为空") LocalDate endDate, @NotNull(message = "status 不能为空") String status) {
    }

    /** 更新请求。 */
    public record CycleUpdateRequest(Long planId, String periodCode, LocalDate startDate, LocalDate endDate, String status) {
    }

    /** 响应。 */
    public record CycleResponse(Long id, Long planId, String periodCode, LocalDate startDate, LocalDate endDate, String status, LocalDateTime createdAt, LocalDateTime updatedAt) {
    }
}
