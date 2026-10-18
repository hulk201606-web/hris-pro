package com.hris.attendance.dto;

import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 请假单 DTO（ATT-F-010 请假申请 / ATT-F-014 代理与冲销）。
 */
public final class LeaveRequestDtos {

    private LeaveRequestDtos() {
    }

    /** 请假申请请求。days 由 start/end 按 8 小时/天换算，不接受客户端传入。 */
    public record LeaveCreateRequest(
            @NotNull(message = "员工 ID 不能为空") Long empId,
            @NotNull(message = "假种 ID 不能为空") Long leaveTypeId,
            @NotNull(message = "开始时间不能为空") LocalDateTime startTime,
            @NotNull(message = "结束时间不能为空") LocalDateTime endTime,
            String reason) {
    }

    /**
     * 销假请求：提前返岗，退还未休假期额度。
     * actualEndTime 之后至原 endTime 的天数回退到额度。
     */
    public record LeaveRevokeRequest(
            @NotNull(message = "实际返岗时间不能为空") LocalDateTime actualEndTime,
            String reason) {
    }

    /** 审批动作请求（审批人意见）。 */
    public record LeaveActionRequest(String remark) {
    }

    /** 请假单响应。 */
    public record LeaveResponse(Long id, Long empId, Long leaveTypeId, String leaveTypeName,
                                String bizKey, LocalDateTime startTime, LocalDateTime endTime,
                                BigDecimal days, String reason, String status, String procInstId,
                                LocalDateTime createdAt, LocalDateTime updatedAt) {
    }
}
