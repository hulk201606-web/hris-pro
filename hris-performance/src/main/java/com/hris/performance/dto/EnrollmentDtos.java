package com.hris.performance.dto;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;

/**
 * Enrollment DTO（由 DDL 自动生成）。
 */
public final class EnrollmentDtos {

    private EnrollmentDtos() {
    }

    /** 创建请求。 */
    public record EnrollmentCreateRequest(@NotNull(message = "sessionId 不能为空") Long sessionId, @NotNull(message = "empId 不能为空") Long empId, @NotNull(message = "status 不能为空") String status) {
    }

    /** 更新请求。 */
    public record EnrollmentUpdateRequest(Long sessionId, Long empId, String status) {
    }

    /** 响应。 */
    public record EnrollmentResponse(Long id, Long sessionId, Long empId, String status, LocalDateTime createdAt, LocalDateTime updatedAt) {
    }
}
