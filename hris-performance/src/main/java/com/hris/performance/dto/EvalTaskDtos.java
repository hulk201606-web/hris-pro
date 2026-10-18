package com.hris.performance.dto;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;

/**
 * EvalTask DTO（由 DDL 自动生成）。
 */
public final class EvalTaskDtos {

    private EvalTaskDtos() {
    }

    /** 创建请求。 */
    public record EvalTaskCreateRequest(@NotNull(message = "cycleId 不能为空") Long cycleId, @NotNull(message = "empId 不能为空") Long empId, @NotNull(message = "evaluatorRole 不能为空") String evaluatorRole, @NotNull(message = "status 不能为空") String status) {
    }

    /** 更新请求。 */
    public record EvalTaskUpdateRequest(Long cycleId, Long empId, String evaluatorRole, String status) {
    }

    /** 响应。 */
    public record EvalTaskResponse(Long id, Long cycleId, Long empId, String evaluatorRole, String status, LocalDateTime createdAt, LocalDateTime updatedAt) {
    }
}
