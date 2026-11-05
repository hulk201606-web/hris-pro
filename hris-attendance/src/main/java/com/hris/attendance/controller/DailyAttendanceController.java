package com.hris.attendance.controller;

import com.hris.attendance.dto.DailyDtos.DailyAttendanceResponse;
import com.hris.attendance.dto.DailyDtos.DailyComputeRequest;
import com.hris.attendance.dto.DailyDtos.DailyComputeResult;
import com.hris.attendance.dto.PageResult;
import com.hris.attendance.dto.PunchDtos.PunchTodayResponse;
import com.hris.attendance.service.DailyAttendanceService;
import com.hris.attendance.service.PunchService;
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
 * 日考勤端点（ATT-F-008 / ATT-F-009）：查询、触发计算与当日自助状态。
 */
@RestController
@RequestMapping("/api/v1/att/daily")
public class DailyAttendanceController {

    private final DailyAttendanceService dailyAttendanceService;
    private final PunchService punchService;

    public DailyAttendanceController(DailyAttendanceService dailyAttendanceService,
                                     PunchService punchService) {
        this.dailyAttendanceService = dailyAttendanceService;
        this.punchService = punchService;
    }

    /**
     * 当日考勤状态（批11#20 统一前端契约，等价于 /att/punches/today）。
     * 「今天」按考勤组时区取，字段：empId/workDate/shiftName/punchIn/punchOut/punchStatus。
     */
    @GetMapping("/today")
    public R<PunchTodayResponse> today(@RequestParam Long empId) {
        return R.ok(punchService.today(empId));
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
