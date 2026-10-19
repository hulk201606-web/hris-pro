package com.hris.performance.dto;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;

/**
 * 报名记录相关 DTO（域：绩效/培训，分层：DTO/接口契约）。报名/退报/完成的请求与响应结构。
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
