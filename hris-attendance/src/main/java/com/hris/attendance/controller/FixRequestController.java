package com.hris.attendance.controller;

import com.hris.attendance.dto.FixDtos.FixActionRequest;
import com.hris.attendance.dto.FixDtos.FixCreateRequest;
import com.hris.attendance.dto.FixDtos.FixResponse;
import com.hris.attendance.dto.PageResult;
import com.hris.attendance.service.FixRequestService;
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
 * 补卡单端点（ATT-F-019）。
 */
@RestController
@RequestMapping("/api/v1/att/fix-requests")
public class FixRequestController {

    private final FixRequestService fixRequestService;

    public FixRequestController(FixRequestService fixRequestService) {
        this.fixRequestService = fixRequestService;
    }

    @GetMapping
    public R<PageResult<FixResponse>> list(
            @RequestParam(required = false) Long empId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long size) {
        return R.ok(fixRequestService.list(empId, status, from, to, page, size));
    }

    @GetMapping("/{id}")
    public R<FixResponse> get(@PathVariable Long id) {
        return R.ok(fixRequestService.get(id));
    }

    @RequirePerm("att:fix:write")
    @PostMapping
    public R<FixResponse> create(@Valid @RequestBody FixCreateRequest req) {
        return R.ok(fixRequestService.create(req));
    }

    @RequirePerm("att:fix:approve")
    @PostMapping("/{id}/approve")
    public R<FixResponse> approve(@PathVariable Long id,
                                  @RequestBody(required = false) FixActionRequest req) {
        return R.ok(fixRequestService.approve(id, req == null ? new FixActionRequest(null) : req));
    }

    @RequirePerm("att:fix:approve")
    @PostMapping("/{id}/reject")
    public R<FixResponse> reject(@PathVariable Long id,
                                 @RequestBody(required = false) FixActionRequest req) {
        return R.ok(fixRequestService.reject(id, req == null ? new FixActionRequest(null) : req));
    }
}
