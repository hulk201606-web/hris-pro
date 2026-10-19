package com.hris.attendance.dto;

import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 日考勤相关 DTO（域：考勤，分层：DTO/接口契约）。日考勤计算触发/结果/分页查询的请求与响应结构。
 */
public final class DailyDtos {

    private DailyDtos() {
    }

    /**
     * 日考勤计算请求。empIds 为空表示对该日期有排班的全部员工重算。
     * 重算为幂等 upsert（uk(tenant_id, emp_id, work_date)）。
     */
    public record DailyComputeRequest(
            @NotNull(message = "工作日不能为空") LocalDate workDate,
            List<Long> empIds) {
    }

    /** 日考勤结果响应。 */
    public record DailyAttendanceResponse(Long id, Long empId, LocalDate workDate, Long employmentId,
                                          String attStatus, LocalDateTime punchIn,
                                          LocalDateTime punchOut, BigDecimal leaveHours,
                                          BigDecimal overtimeHours, LocalDateTime updatedAt) {
    }

    /** 日考勤计算批次结果（含状态分布与新生成异常数）。 */
    public record DailyComputeResult(LocalDate workDate, int employeeCount, int normalCount,
                                     int lateCount, int earlyCount, int absentCount, int leaveCount,
                                     int newExceptionCount) {
    }
}
