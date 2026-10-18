package com.hris.performance.dto;

import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Goal DTO（由 DDL 自动生成）。
 */
public final class GoalDtos {

    private GoalDtos() {
    }

    /** 创建请求。 */
    public record GoalCreateRequest(@NotNull(message = "cycleId 不能为空") Long cycleId, @NotNull(message = "empId 不能为空") Long empId, @NotNull(message = "title 不能为空") String title, @NotNull(message = "weight 不能为空") BigDecimal weight, String targetValue, String resultValue) {
    }

    /** 更新请求。 */
    public record GoalUpdateRequest(Long cycleId, Long empId, String title, BigDecimal weight, String targetValue, String resultValue) {
    }

    /** 响应。 */
    public record GoalResponse(Long id, Long cycleId, Long empId, String title, BigDecimal weight, String targetValue, String resultValue, LocalDateTime createdAt, LocalDateTime updatedAt) {
    }
}
