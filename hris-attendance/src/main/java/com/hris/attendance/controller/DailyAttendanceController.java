package com.hris.attendance.controller;

import com.hris.attendance.dto.DailyDtos.DailyAttendanceResponse;
import com.hris.attendance.dto.DailyDtos.DailyComputeRequest;
import com.hris.attendance.dto.DailyDtos.DailyComputeResult;
import com.hris.attendance.dto.PageResult;
import com.hris.attendance.service.DailyAttendanceService;
import com.hris.common.api.R;
import com.hris.common.security.RequirePerm;
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

/**
 * 日考勤端点（ATT-F-008 / ATT-F-009）：查询与触发计算。
 */
@RestController
@RequestMapping("/api/v1/att/daily")
public class DailyAttendanceController {

    private final DailyAttendanceService dailyAttendanceService;

    public DailyAttendanceController(DailyAttendanceService dailyAttendanceService) {
        this.dailyAttendanceService = dailyAttendanceService;
    }
    /** 分页查询列表。权限点见 @RequirePerm。 */

    @GetMapping
    public R<PageResult<DailyAttendanceResponse>> list(
            @RequestParam(required = false) Long empId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String attStatus,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long size) {
        return R.ok(dailyAttendanceService.list(empId, from, to, attStatus, page, size));
    }
    /** 按 ID 查询详情。权限点见 @RequirePerm。 */

    @GetMapping("/{empId}/{workDate}")
    public R<DailyAttendanceResponse> get(@PathVariable Long empId,
                                          @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate workDate) {
        return R.ok(dailyAttendanceService.get(empId, workDate));
    }

    @RequirePerm("att:daily:compute")
    @PostMapping("/compute")
    public R<DailyComputeResult> compute(@Valid @RequestBody DailyComputeRequest req) {
        return R.ok(dailyAttendanceService.compute(req));
    }
}
