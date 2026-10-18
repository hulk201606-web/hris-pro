package com.hris.attendance.controller;

import com.hris.attendance.dto.ReportDtos.AttendanceRateItem;
import com.hris.attendance.dto.ReportDtos.LeaveConsumeItem;
import com.hris.attendance.dto.ReportDtos.OvertimeStatItem;
import com.hris.attendance.service.ReportService;
import com.hris.common.api.R;
import com.hris.common.security.RequirePerm;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/**
 * 考勤报表端点（ATT-F-025）：出勤率 / 加班时长 / 假期消耗。
 */
@RestController
@RequestMapping("/api/v1/att/reports")
public class ReportController {

    private final ReportService reportService;

    public ReportController(ReportService reportService) {
        this.reportService = reportService;
    }

    @RequirePerm("att:report:read")
    @GetMapping("/attendance-rate")
    public R<List<AttendanceRateItem>> attendanceRate(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return R.ok(reportService.attendanceRate(from, to));
    }

    @RequirePerm("att:report:read")
    @GetMapping("/overtime")
    public R<List<OvertimeStatItem>> overtime(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return R.ok(reportService.overtimeStat(from, to));
    }

    @RequirePerm("att:report:read")
    @GetMapping("/leave-consume")
    public R<List<LeaveConsumeItem>> leaveConsume(@RequestParam String period) {
        return R.ok(reportService.leaveConsume(period));
    }
}
