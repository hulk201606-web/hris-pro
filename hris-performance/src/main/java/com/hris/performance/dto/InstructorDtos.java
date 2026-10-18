package com.hris.performance.dto;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;

/**
 * Instructor DTO（由 DDL 自动生成）。
 */
public final class InstructorDtos {

    private InstructorDtos() {
    }

    /** 创建请求。 */
    public record InstructorCreateRequest(@NotNull(message = "empId 不能为空") Long empId) {
    }

    /** 更新请求。 */
    public record InstructorUpdateRequest(Long empId) {
    }

    /** 响应。 */
    public record InstructorResponse(Long id, Long empId, LocalDateTime createdAt, LocalDateTime updatedAt) {
    }
}
