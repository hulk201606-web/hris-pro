package com.hris.performance.dto;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;

/**
 * OKR 目标相关 DTO（域：绩效，分层：DTO/接口契约）。O 与 KR 录入/查询的请求与响应结构。
 */
public final class OkrObjectiveDtos {

    private OkrObjectiveDtos() {
    }

    /** 创建请求。 */
    public record OkrObjectiveCreateRequest(@NotNull(message = "cycleId 不能为空") Long cycleId, @NotNull(message = "empId 不能为空") Long empId, @NotNull(message = "objective 不能为空") String objective, String keyResults) {
    }

    /** 更新请求。 */
    public record OkrObjectiveUpdateRequest(Long cycleId, Long empId, String objective, String keyResults) {
    }

    /** 响应。 */
    public record OkrObjectiveResponse(Long id, Long cycleId, Long empId, String objective, String keyResults, LocalDateTime createdAt, LocalDateTime updatedAt) {
    }
}
