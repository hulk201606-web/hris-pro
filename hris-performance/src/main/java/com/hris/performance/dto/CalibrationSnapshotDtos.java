package com.hris.performance.dto;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;

/**
 * 校准快照相关 DTO（域：绩效，分层：DTO/接口契约）。快照查询与回溯的数据结构。
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
