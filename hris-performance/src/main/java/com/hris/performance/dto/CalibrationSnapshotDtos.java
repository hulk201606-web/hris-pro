package com.hris.performance.dto;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;

/**
 * CalibrationSnapshot DTO（由 DDL 自动生成）。
 */
public final class CalibrationSnapshotDtos {

    private CalibrationSnapshotDtos() {
    }

    /** 创建请求。 */
    public record CalibrationSnapshotCreateRequest(@NotNull(message = "sessionId 不能为空") Long sessionId, @NotNull(message = "snapshot 不能为空") String snapshot) {
    }

    /** 更新请求。 */
    public record CalibrationSnapshotUpdateRequest(Long sessionId, String snapshot) {
    }

    /** 响应。 */
    public record CalibrationSnapshotResponse(Long id, Long sessionId, String snapshot, LocalDateTime createdAt, LocalDateTime updatedAt) {
    }
}
