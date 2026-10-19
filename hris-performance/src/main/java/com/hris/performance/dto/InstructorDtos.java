package com.hris.performance.dto;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;

/**
 * 讲师相关 DTO（域：绩效/培训，分层：DTO/接口契约）。讲师主档的请求与响应结构。
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
