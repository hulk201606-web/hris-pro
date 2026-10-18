package com.hris.employee.dto;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

/**
 * 转正单请求（EMP-F-008）。并发起 EMP_REGULAR 流程（MVP 落单 + 预留 flow）。
 */
public record RegularizeRequest(
        @NotNull(message = "empId 不能为空") Long empId,
        @NotNull(message = "regularDate 不能为空") LocalDate regularDate,
        String comment,
        String reason) {
}
