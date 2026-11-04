package com.hris.platform.dto;

import jakarta.validation.constraints.NotBlank;

import java.time.LocalDateTime;
import java.util.List;

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

    /**
     * 预置报表目录项（PLT-F-005）。
     *
     * <p>目录是产品侧约定好的五类主题，先以常量形式对外暴露，避免每个租户
     * 都要手工 seed 一遍。业务数据取数依赖 PLT-F-006 只读聚合库，未就绪前
     * 导出内容只含报表定义清单，见 ExportTaskDispatchHandler。</p>
     */
    public record CatalogItem(String code, String name, String description, List<String> formats) {
    }
}
