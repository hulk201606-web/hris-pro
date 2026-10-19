package com.hris.compensation.dto;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;

/**
 * 导入批次相关 DTO（域：薪酬，分层：DTO/接口契约）。导入批次创建/查询/状态的请求与响应结构。
 */
public final class ImportBatchDtos {

    private ImportBatchDtos() {
    }

    /** 创建请求。 */
    public record ImportBatchCreateRequest(@NotNull(message = "batchNo 不能为空") String batchNo, @NotNull(message = "importType 不能为空") String importType, @NotNull(message = "totalRows 不能为空") Integer totalRows, @NotNull(message = "successRows 不能为空") Integer successRows, @NotNull(message = "failRows 不能为空") Integer failRows, @NotNull(message = "status 不能为空") String status) {
    }

    /** 更新请求。 */
    public record ImportBatchUpdateRequest(String batchNo, String importType, Integer totalRows, Integer successRows, Integer failRows, String status) {
    }

    /** 响应。 */
    public record ImportBatchResponse(Long id, String batchNo, String importType, Integer totalRows, Integer successRows, Integer failRows, String status, LocalDateTime createdAt, LocalDateTime updatedAt) {
    }
}
