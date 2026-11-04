package com.hris.platform.dto;

import jakarta.validation.constraints.NotBlank;

import java.time.LocalDateTime;

/** 任务调度中心 DTO（PLT-F-011）。 */
public final class SchedulerDtos {

    private SchedulerDtos() {
    }

    /** 新建/更新注册项。status 为 null 时默认启用。 */
    public record TaskRequest(
            @NotBlank(message = "任务键不能为空") String taskKey,
            @NotBlank(message = "cron 不能为空") String cron,
            @NotBlank(message = "处理器不能为空") String handler,
            Integer status) {
    }

    /** 注册项（含运行时是否已成功装载）。 */
    public record TaskResponse(
            Long id,
            String taskKey,
            String cron,
            String handler,
            Integer status,
            Boolean scheduled,
            LocalDateTime updatedAt) {
    }

    /** 执行日志。 */
    public record TaskLogResponse(
            Long id,
            Long taskId,
            String taskKey,
            String handler,
            String triggerType,
            String status,
            Integer retryNo,
            Long durationMs,
            String message,
            LocalDateTime createdAt) {
    }

    /** 一次手动触发的结果。 */
    public record RunResponse(String taskKey, String status, String message) {
    }

    /** 可选处理器。 */
    public record HandlerOption(String key, String description) {
    }
}
