package com.hris.platform.dto;

import java.time.LocalDate;

/**
 * 试用期到期提醒项（EMP-F-007 深化）。
 *
 * <p>与 hris-employee 的 EmployeeDtos.ProbationReminderItem 契约对齐
 * （跨服务 DTO 不共享，按 docs/07 内部契约各自声明）。</p>
 */
public record ProbationReminderItem(
        Long contractId, Long empId, String empNo, String name, Long deptId,
        LocalDate probationEnd, long daysRemaining, String level) {
}
