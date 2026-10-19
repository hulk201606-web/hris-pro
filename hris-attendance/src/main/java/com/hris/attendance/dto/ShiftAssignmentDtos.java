package com.hris.attendance.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/**
 * 排班分配相关 DTO（域：考勤，分层：DTO/接口契约）。批量排班/周期排班的请求与响应结构。
 */
public final class ShiftAssignmentDtos {

    /** 周期模板：全部排班。 */
    public static final String PATTERN_DAILY = "DAILY";
    /** 周期模板：做一休一。 */
    public static final String PATTERN_REST_ONE = "REST_ONE";
    /** 周期模板：大小周（单周排周一至周五，双周加排周六）。 */
    public static final String PATTERN_BIG_SMALL_WEEK = "BIG_SMALL_WEEK";

    private ShiftAssignmentDtos() {
    }

    /** 批量排班请求：区间内每人每天绑定同一班次。 */
    public record AssignmentBatchRequest(
            @NotEmpty(message = "员工列表不能为空") List<Long> empIds,
            @NotNull(message = "班次不能为空") Long shiftId,
            @NotNull(message = "开始日期不能为空") LocalDate fromDate,
            @NotNull(message = "结束日期不能为空") LocalDate toDate) {
    }

    /** 周期模板排班请求：pattern 见本类常量。 */
    public record AssignmentCycleRequest(
            @NotEmpty(message = "员工列表不能为空") List<Long> empIds,
            @NotNull(message = "班次不能为空") Long shiftId,
            @NotNull(message = "开始日期不能为空") LocalDate fromDate,
            @NotNull(message = "结束日期不能为空") LocalDate toDate,
            @NotBlank(message = "周期模板不能为空") String pattern) {
    }

    /** 排班响应（含班次快照字段，避免前端二次查询）。 */
    public record AssignmentResponse(Long id, Long empId, Long shiftId, LocalDate workDate,
                                     String shiftName, LocalTime startTime, LocalTime endTime) {
    }

    /** 批量排班结果。 */
    public record AssignmentBatchResult(int requested, int created, int updated, int skipped) {
    }
}
