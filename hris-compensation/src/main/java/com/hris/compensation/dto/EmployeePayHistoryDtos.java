package com.hris.compensation.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 调薪轨迹 DTO（CMP-F-018）。
 *
 * <p>覆盖度：source=PAY_VERSION 来自 cmp_employee_pay 版本链（定薪档案，权威）；
 * source=ADJUSTMENT 来自 cmp_payroll_adjustment 调整单（仅补充该月无版本记录的变更，
 * 金额为调整额而非定薪额，见 reason 前缀）。approvalId 当前库表无审批单关联列，恒为 null，
 * 待后续批次补审批载体后回填。</p>
 */
public final class EmployeePayHistoryDtos {

    private EmployeePayHistoryDtos() {
    }

    /** 单条调薪轨迹（按 effectiveDate 倒序返回）。 */
    public record EmployeePayHistoryItem(LocalDate effectiveDate, BigDecimal baseAmount, String reason,
                                         Long approvalId, Long createdBy, LocalDateTime createdAt,
                                         String source) {
    }
}
