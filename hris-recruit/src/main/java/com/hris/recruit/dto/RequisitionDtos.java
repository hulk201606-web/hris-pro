package com.hris.recruit.dto;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;

/**
 * Requisition DTO（由 DDL 自动生成）。
 */
public final class RequisitionDtos {

    private RequisitionDtos() {
    }

    /** 创建请求。 */
    public record RequisitionCreateRequest(@NotNull(message = "reqNo 不能为空") String reqNo, @NotNull(message = "deptId 不能为空") Long deptId, @NotNull(message = "positionId 不能为空") Long positionId, @NotNull(message = "headcount 不能为空") Integer headcount, String reason, @NotNull(message = "status 不能为空") String status) {
    }

    /** 更新请求。 */
    public record RequisitionUpdateRequest(String reqNo, Long deptId, Long positionId, Integer headcount, String reason, String status) {
    }

    /** 响应。 */
    public record RequisitionResponse(Long id, String reqNo, Long deptId, Long positionId, Integer headcount, String reason, String status, LocalDateTime createdAt, LocalDateTime updatedAt) {
    }
}
