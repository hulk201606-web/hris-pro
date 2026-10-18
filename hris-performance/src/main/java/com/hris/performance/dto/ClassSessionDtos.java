package com.hris.performance.dto;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;

/**
 * ClassSession DTO（由 DDL 自动生成）。
 */
public final class ClassSessionDtos {

    private ClassSessionDtos() {
    }

    /** 创建请求。 */
    public record ClassSessionCreateRequest(@NotNull(message = "courseId 不能为空") Long courseId, Long instructorId, LocalDateTime startAt, @NotNull(message = "capacity 不能为空") Integer capacity) {
    }

    /** 更新请求。 */
    public record ClassSessionUpdateRequest(Long courseId, Long instructorId, LocalDateTime startAt, Integer capacity) {
    }

    /** 响应。 */
    public record ClassSessionResponse(Long id, Long courseId, Long instructorId, LocalDateTime startAt, Integer capacity, LocalDateTime createdAt, LocalDateTime updatedAt) {
    }
}
