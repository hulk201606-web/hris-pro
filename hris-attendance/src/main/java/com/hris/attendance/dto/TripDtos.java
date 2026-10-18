package com.hris.attendance.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 出差单 DTO（ATT-F-017 出差申请，补贴天数输出给 CMP）。
 */
public final class TripDtos {

    private TripDtos() {
    }

    /** 出差申请请求。destination 必填，行程需与已审批请假单互斥。 */
    public record TripCreateRequest(
            @NotNull(message = "员工 ID 不能为空") Long empId,
            @NotNull(message = "开始时间不能为空") LocalDateTime startTime,
            @NotNull(message = "结束时间不能为空") LocalDateTime endTime,
            @NotBlank(message = "目的地不能为空") String destination,
            String reason) {
    }

    /** 审批动作请求。 */
    public record TripActionRequest(String remark) {
    }

    /** 出差单响应。subsidyDays 按自然日跨度派生，供薪酬域计算出差补贴。 */
    public record TripResponse(Long id, Long empId, String bizKey, LocalDateTime startTime,
                               LocalDateTime endTime, String destination, String reason,
                               String status, String procInstId, BigDecimal subsidyDays,
                               LocalDateTime createdAt, LocalDateTime updatedAt) {
    }
}
