package com.hris.performance.dto;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;

/**
 * 绩效申诉相关 DTO（域：绩效，分层：DTO/接口契约）。申诉创建/查询/处理的请求与响应结构。
 */
public final class AppealDtos {

    private AppealDtos() {
    }

    /**
     * 创建请求（服务端强制语义：status 恒置 PROCESSING、biz_key 服务端生成、
     * proc_inst_id 仅由流程发起回写，调用方均不可指定）。
     */
    public record AppealCreateRequest(@NotNull(message = "empId 不能为空") Long empId, @NotNull(message = "cycleId 不能为空") Long cycleId, String reason) {
    }

    /**
     * 更新请求（旁路清理：biz_key/status/proc_inst_id 均不可经更新改写——
     * status 只由审批回调迁移，proc_inst_id 只由发起回写）。
     */
    public record AppealUpdateRequest(Long empId, Long cycleId, String reason) {
    }

    /** 响应。 */
    public record AppealResponse(Long id, Long empId, Long cycleId, String bizKey, String reason, String status, String procInstId, LocalDateTime createdAt, LocalDateTime updatedAt) {
    }
}
