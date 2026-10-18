package com.hris.employee.dto;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

/**
 * 调动单请求（EMP-F-009）。并发起 EMP_TRANSFER 流程（MVP 落单 + 预留 flow）。
 */
public record TransferRequest(
        @NotNull(message = "empId 不能为空") Long empId,
        @NotNull(message = "targetDeptId 不能为空") Long targetDeptId,
        @NotNull(message = "targetPositionId 不能为空") Long targetPositionId,
        Long targetGradeId,
        Long targetSupervisorEmpId,
        @NotNull(message = "effectDate 不能为空") LocalDate effectDate,
        String reason) {
}
