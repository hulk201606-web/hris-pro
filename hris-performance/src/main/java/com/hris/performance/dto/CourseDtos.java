package com.hris.performance.dto;

import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 课程相关 DTO（域：绩效/培训，分层：DTO/接口契约）。课程创建/更新/查询的请求与响应结构。
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
