package com.hris.performance.dto;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;

/**
 * 评估任务相关 DTO（域：绩效，分层：DTO/接口契约）。任务指派/提交/查询的请求与响应结构。
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
