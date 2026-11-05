package com.hris.employee.client;

import com.hris.common.api.R;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 假勤域自助取数（SELF-F-001 首页聚合）。
 *
 * <p>全部为只读内部/自助端点；超时由 {@code SelfFeignConfig} 收敛为 1s，
 * 调用方（SelfDashboardService）对每个子项独立 fail-open。</p>
 */
@FeignClient(name = "hris-attendance", contextId = "empAttSelfClient",
        configuration = SelfFeignConfig.class)
public interface AttSelfClient {

    /** 今日日考勤（内部端点），无记录返回空列表。 */
    @GetMapping("/internal/att/daily")
    R<List<DailyAttendance>> daily(@RequestParam("empId") Long empId,
                                   @RequestParam("from") LocalDate from,
                                   @RequestParam("to") LocalDate to);

    /** 期间汇总（内部端点）：迟到次数/缺勤天数。 */
    @GetMapping("/internal/att/attendance-brief")
    R<List<AttendanceBrief>> attendanceBrief(@RequestParam("empIds") List<Long> empIds,
                                             @RequestParam("periodStart") LocalDate periodStart,
                                             @RequestParam("periodEnd") LocalDate periodEnd);

    /** 我的假期余额（自助端点，支持显式 empId）。 */
    @GetMapping("/api/v1/att/leave-quotas/me")
    R<QuotaBalance> myQuotas(@RequestParam("empId") Long empId);

    /** 日考勤状态。 */
    record DailyAttendance(String attStatus) {
    }

    /** 期间汇总。 */
    record AttendanceBrief(Long empId, long lateCount, long absentDays) {
    }

    /** 我的假期余额。 */
    record QuotaBalance(Long empId, List<LeaveQuota> quotas) {
    }

    /** 单假种余额。 */
    record LeaveQuota(Long leaveTypeId, String leaveTypeName, BigDecimal availableQuota) {
    }
}
