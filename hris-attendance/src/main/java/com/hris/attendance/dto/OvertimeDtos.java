package com.hris.attendance.dto;

import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 加班单 DTO（ATT-F-016 加班申请与统计）。
 */
public final class OvertimeDtos {

    /** 加班模式：事前审批。 */
    public static final String MODE_BEFORE = "BEFORE";
    /** 加班模式：事后补录。 */
    public static final String MODE_AFTER = "AFTER";

    private OvertimeDtos() {
    }

    /** 加班申请请求。hours 由 start/end 换算，不接受客户端传入。 */
    public record OvertimeCreateRequest(
            @NotNull(message = "员工 ID 不能为空") Long empId,
            @NotNull(message = "开始时间不能为空") LocalDateTime startTime,
            @NotNull(message = "结束时间不能为空") LocalDateTime endTime,
            String reason,
            String mode) {
    }

    /** 审批动作请求。 */
    public record OvertimeActionRequest(String remark) {
    }

    /**
     * 加班单响应。dayType 与 rate 由 starts 日期派生（工作日 1.5 / 休息日 2.0 / 法定节假日 3.0），
     * 因 DDL 无对应列故不落库；法定节假日需节假日历支持，MVP 暂按周末判定。
     */
    public record OvertimeResponse(Long id, Long empId, String bizKey, LocalDateTime startTime,
                                   LocalDateTime endTime, BigDecimal hours, String reason,
                                   String mode, String status, String procInstId, String dayType,
                                   BigDecimal rate, LocalDateTime createdAt,
                                   LocalDateTime updatedAt) {
    }
}
