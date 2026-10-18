package com.hris.attendance.controller;

import com.hris.attendance.dto.OvertimeDtos.OvertimeActionRequest;
import com.hris.attendance.dto.OvertimeDtos.OvertimeCreateRequest;
import com.hris.attendance.dto.OvertimeDtos.OvertimeResponse;
import com.hris.attendance.dto.PageResult;
import com.hris.attendance.service.OvertimeService;
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
 * 加班单端点（ATT-F-016）。
 */
@RestController
@RequestMapping("/api/v1/att/overtime-requests")
public class OvertimeController {

    private final OvertimeService overtimeService;

    public OvertimeController(OvertimeService overtimeService) {
        this.overtimeService = overtimeService;
    }

    @GetMapping
    public R<PageResult<OvertimeResponse>> list(
            @RequestParam(required = false) Long empId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long size) {
        return R.ok(overtimeService.list(empId, status, from, to, page, size));
    }

    @GetMapping("/{id}")
    public R<OvertimeResponse> get(@PathVariable Long id) {
        return R.ok(overtimeService.get(id));
    }

    @RequirePerm("att:overtime:write")
    @PostMapping
    public R<OvertimeResponse> create(@Valid @RequestBody OvertimeCreateRequest req) {
        return R.ok(overtimeService.create(req));
    }

    @RequirePerm("att:overtime:approve")
    @PostMapping("/{id}/approve")
    public R<OvertimeResponse> approve(@PathVariable Long id,
                                       @RequestBody(required = false) OvertimeActionRequest req) {
        return R.ok(overtimeService.approve(id, req == null ? new OvertimeActionRequest(null) : req));
    }

    @RequirePerm("att:overtime:approve")
    @PostMapping("/{id}/reject")
    public R<OvertimeResponse> reject(@PathVariable Long id,
                                      @RequestBody(required = false) OvertimeActionRequest req) {
        return R.ok(overtimeService.reject(id, req == null ? new OvertimeActionRequest(null) : req));
    }
}
