package com.hris.recruit.dto;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;

/**
 * Requisition DTO（由 DDL 自动生成）。
 */
public final class RequisitionDtos {

    private RequisitionDtos() {
    }

    /**
     * 创建请求。状态不接受入参：新建固定 DRAFT，提交走 submit 端点（防状态机旁路）。
     */
    public record RequisitionCreateRequest(@NotNull(message = "reqNo 不能为空") String reqNo, @NotNull(message = "deptId 不能为空") Long deptId, @NotNull(message = "positionId 不能为空") Long positionId, @NotNull(message = "headcount 不能为空") Integer headcount, String reason) {
    }

    /**
     * 更新请求。状态不接受入参：仅可经 submit 端点或审批回调迁移（防状态机旁路）。
     */
    public record RequisitionUpdateRequest(String reqNo, Long deptId, Long positionId, Integer headcount, String reason) {
    }

    /** 响应。published：官网发布标记 0/1（RCS-F-004）。 */
    public record RequisitionResponse(Long id, String reqNo, Long deptId, Long positionId, Integer headcount, String reason, String status, Integer published, LocalDateTime createdAt, LocalDateTime updatedAt) {
    }
}
