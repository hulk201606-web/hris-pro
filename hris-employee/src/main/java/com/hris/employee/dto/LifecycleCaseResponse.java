package com.hris.employee.dto;

import java.time.LocalDate;

/**
 * 生命周期单响应（入转调离落单结果）。
 */
public record LifecycleCaseResponse(
        Long id, Long empId, String type, String bizKey,
        String state, LocalDate effectDate, String flowInstanceId) {
}
