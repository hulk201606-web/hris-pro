package com.hris.attendance.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 考勤异常 DTO（ATT-F-020 异常处理工作台）。
 */
public final class ExceptionDtos {

    /** 处理动作：认定（销异常，保留事实）。 */
    public static final String ACTION_RESOLVE = "RESOLVE";
    /** 处理动作：豁免（不纳入月结考核）。 */
    public static final String ACTION_IGNORE = "IGNORE";

    private ExceptionDtos() {
    }

    /** 异常响应。 */
    public record ExceptionResponse(Long id, Long empId, LocalDate workDate, String exceptionType,
                                    String status, String description, LocalDateTime createdAt) {
    }

    /** 单条异常处理请求。action 见本类常量。 */
    public record ExceptionHandleRequest(
            @NotBlank(message = "处理动作不能为空") String action,
            String remark) {
    }

    /** 批量异常处理请求。 */
    public record ExceptionBatchHandleRequest(
            @NotEmpty(message = "异常单列表不能为空") List<Long> ids,
            @NotBlank(message = "处理动作不能为空") String action,
            String remark) {
    }

    /** 批量处理汇总。 */
    public record ExceptionHandleResult(int total, int success, List<Long> failedIds) {
    }
}
