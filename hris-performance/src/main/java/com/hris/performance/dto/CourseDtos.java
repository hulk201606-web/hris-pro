package com.hris.performance.dto;

import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Course DTO（由 DDL 自动生成）。
 */
public final class CourseDtos {

    private CourseDtos() {
    }

    /** 创建请求。 */
    public record CourseCreateRequest(@NotNull(message = "name 不能为空") String name, @NotNull(message = "code 不能为空") String code, @NotNull(message = "hours 不能为空") BigDecimal hours) {
    }

    /** 更新请求。 */
    public record CourseUpdateRequest(String name, String code, BigDecimal hours) {
    }

    /** 响应。 */
    public record CourseResponse(Long id, String name, String code, BigDecimal hours, LocalDateTime createdAt, LocalDateTime updatedAt) {
    }
}
