package com.hris.attendance.controller;

import com.hris.attendance.dto.ApprovalDtos.ApprovalCallbackRequest;
import com.hris.attendance.dto.ApprovalDtos.ApprovalCallbackResponse;
import com.hris.attendance.dto.ClosingDtos.ClosingDetailResponse;
import com.hris.attendance.dto.DailyDtos.DailyAttendanceResponse;
import com.hris.attendance.service.ApprovalService;
import com.hris.attendance.service.DailyAttendanceService;
import com.hris.attendance.service.MonthlyClosingService;
import com.hris.common.api.R;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/**
 * 假勤域内部端点（网关封禁外部访问，Feign 双向签名）。
 *
 * <p>消费方：hris-compensation（算薪取数）、hris-flow（审批结果回调）。</p>
 */
@RestController
@RequestMapping("/internal/att")
public class AttendanceInternalController {

    private final DailyAttendanceService dailyAttendanceService;
    private final MonthlyClosingService monthlyClosingService;
    private final ApprovalService approvalService;

    public AttendanceInternalController(DailyAttendanceService dailyAttendanceService,
                                        MonthlyClosingService monthlyClosingService,
                                        ApprovalService approvalService) {
        this.dailyAttendanceService = dailyAttendanceService;
        this.monthlyClosingService = monthlyClosingService;
        this.approvalService = approvalService;
    }

    /** 日考勤取数（按员工 + 日期区间），供薪酬域按周期取明细。 */
    @GetMapping("/daily")
    public R<List<DailyAttendanceResponse>> daily(@RequestParam Long empId,
                                                  @RequestParam LocalDate from,
                                                  @RequestParam LocalDate to) {
        List<DailyAttendanceResponse> records = dailyAttendanceService
                .findByRange(from, to).stream()
                .filter(d -> empId.equals(d.getEmpId()))
                .map(d -> new DailyAttendanceResponse(d.getId(), d.getEmpId(), d.getWorkDate(),
                        d.getEmploymentId(), d.getAttStatus(), d.getPunchIn(), d.getPunchOut(),
                        d.getLeaveHours(), d.getOvertimeHours(), d.getUpdatedAt()))
                .toList();
        return R.ok(records);
    }

    /** 月结结果集（按周期），供薪酬域取汇总。 */
    @GetMapping("/monthly/{periodCode}")
    public R<ClosingDetailResponse> monthly(@PathVariable String periodCode) {
        return R.ok(monthlyClosingService.detail(periodCode));
    }

    /** 审批结果回调（幂等）。 */
    @PostMapping("/approval-callback")
    public R<ApprovalCallbackResponse> approvalCallback(
            @Valid @RequestBody ApprovalCallbackRequest req) {
        return R.ok(approvalService.callback(req));
    }
}
