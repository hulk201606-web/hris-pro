package com.hris.performance.dto;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;

/**
 * 校准会话相关 DTO（域：绩效，分层：DTO/接口契约）。校准会议创建/查询/状态流转的请求与响应结构。
 */
public final class CalibrationSessionDtos {

    private CalibrationSessionDtos() {
    }

    /** 创建请求。 */
    public record CalibrationSessionCreateRequest(@NotNull(message = "cycleId 不能为空") Long cycleId, @NotNull(message = "deptId 不能为空") Long deptId, @NotNull(message = "status 不能为空") String status) {
    }

    /** 更新请求。 */
    public record CalibrationSessionUpdateRequest(Long cycleId, Long deptId, String status) {
    }

    /** 响应。 */
    public record CalibrationSessionResponse(Long id, Long cycleId, Long deptId, String status, LocalDateTime createdAt, LocalDateTime updatedAt) {
    }
}
