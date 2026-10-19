package com.hris.performance.dto;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;

/**
 * 绩效申诉相关 DTO（域：绩效，分层：DTO/接口契约）。申诉创建/查询/处理的请求与响应结构。
 */
public final class AppealDtos {

    private AppealDtos() {
    }

    /** 创建请求。 */
    public record AppealCreateRequest(@NotNull(message = "empId 不能为空") Long empId, @NotNull(message = "cycleId 不能为空") Long cycleId, @NotNull(message = "bizKey 不能为空") String bizKey, String reason, @NotNull(message = "status 不能为空") String status, String procInstId) {
    }

    /** 更新请求。 */
    public record AppealUpdateRequest(Long empId, Long cycleId, String bizKey, String reason, String status, String procInstId) {
    }

    /** 响应。 */
    public record AppealResponse(Long id, Long empId, Long cycleId, String bizKey, String reason, String status, String procInstId, LocalDateTime createdAt, LocalDateTime updatedAt) {
    }
}
