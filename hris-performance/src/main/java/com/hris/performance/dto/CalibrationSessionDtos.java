package com.hris.performance.dto;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;

/**
 * CalibrationSession DTO（由 DDL 自动生成）。
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
