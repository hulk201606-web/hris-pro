package com.hris.attendance.controller;

import com.hris.attendance.dto.PageResult;
import com.hris.attendance.dto.PunchDtos.PunchCreateRequest;
import com.hris.attendance.dto.PunchDtos.PunchResponse;
import com.hris.attendance.dto.PunchDtos.PunchTodayResponse;
import com.hris.attendance.service.PunchService;
import com.hris.common.api.R;
import com.hris.common.security.RequirePerm;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

/**
 * 打卡端点（ATT-F-004 / ATT-F-006 / ATT-F-007）。
 */
@RestController
@RequestMapping("/api/v1/att/punches")
public class PunchController {

    private final PunchService punchService;

    public PunchController(PunchService punchService) {
        this.punchService = punchService;
    }
    /** 分页查询列表。权限点见 @RequirePerm。 */

    @GetMapping
    public R<PageResult<PunchResponse>> list(
            @RequestParam(required = false) Long empId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long size) {
        return R.ok(punchService.list(empId, from, to, page, size));
    }

    @GetMapping("/today")
    public R<PunchTodayResponse> today(@RequestParam Long empId) {
        return R.ok(punchService.today(empId));
    }

    @RequirePerm("att:punch:write")
    @PostMapping
    public R<PunchResponse> punch(@Valid @RequestBody PunchCreateRequest req) {
        return R.ok(punchService.punch(req));
    }
}
