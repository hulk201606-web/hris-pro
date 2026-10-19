package com.hris.attendance.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 打卡相关 DTO（域：考勤，分层：DTO/接口契约）。打卡提交/当日打卡查询的请求与响应结构。
 */
public final class PunchDtos {

    private PunchDtos() {
    }

    /**
     * 打卡请求。punch_time 为客户端采集的 UTC 时刻，
     * 幂等键为 uk(tenant_id, emp_id, punch_time)。
     */
    public record PunchCreateRequest(
            @NotNull(message = "员工 ID 不能为空") Long empId,
            @NotNull(message = "打卡时间不能为空") LocalDateTime punchTime,
            @NotBlank(message = "打卡类型不能为空") String type,
            String source,
            String location,
            String deviceId,
            Long attGroupId) {
    }

    /** 打卡记录响应。 */
    public record PunchResponse(Long id, Long empId, LocalDateTime punchTime, String type,
                                String source, String location, String deviceId, Long attGroupId,
                                Integer status, LocalDateTime createdAt) {
    }

    /** 当日考勤状态响应（员工自助）。 */
    public record PunchTodayResponse(Long empId, LocalDate workDate, String shiftName,
                                     LocalDateTime punchIn, LocalDateTime punchOut,
                                     String punchStatus) {
    }
}
