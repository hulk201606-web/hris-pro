package com.hris.employee.dto;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

/**
 * 离职单请求（EMP-F-011）。并发起 EMP_RESIGN 流程（MVP 落单 + 预留 flow）。
 */
public record ResignRequest(
        @NotNull(message = "empId 不能为空") Long empId,
        @NotNull(message = "lastWorkDate 不能为空") LocalDate lastWorkDate,
        String resignType,
        String reason) {
}
