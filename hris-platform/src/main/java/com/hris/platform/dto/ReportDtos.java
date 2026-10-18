package com.hris.platform.dto;

import jakarta.validation.constraints.NotBlank;

import java.time.LocalDateTime;

/**
 * 报表中心 DTO（PLT-F-005）。
 */
public final class ReportDtos {

    private ReportDtos() {
    }

    /** 报表定义创建/更新请求（code 为空时自动生成） */
    public record ReportDefRequest(
            @NotBlank(message = "报表名称不能为空") String name,
            String code,
            String defJson) {
    }

    /** 报表定义响应 */
    public record ReportDefResponse(
            Long id,
            String name,
            String code,
            String defJson,
            LocalDateTime updatedAt) {
    }

    /** 导出任务创建请求 */
    public record ExportTaskRequest(Long reportDefId) {
    }

    /** 导出任务响应 */
    public record ExportTaskResponse(
            Long id,
            Long reportDefId,
            String status,
            Long fileId,
            LocalDateTime createdAt) {
    }
}
