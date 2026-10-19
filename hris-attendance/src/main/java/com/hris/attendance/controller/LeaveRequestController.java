package com.hris.attendance.controller;

import com.hris.attendance.dto.LeaveRequestDtos.LeaveActionRequest;
import com.hris.attendance.dto.LeaveRequestDtos.LeaveCreateRequest;
import com.hris.attendance.dto.LeaveRequestDtos.LeaveResponse;
import com.hris.attendance.dto.LeaveRequestDtos.LeaveRevokeRequest;
import com.hris.attendance.dto.PageResult;
import com.hris.attendance.service.LeaveQueryService;
import com.hris.attendance.service.LeaveRequestService;
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
 * 请假单端点（ATT-F-010 / ATT-F-014）。
 */
@RestController
@RequestMapping("/api/v1/att/leave-requests")
public class LeaveRequestController {

    private final LeaveRequestService leaveRequestService;
    private final LeaveQueryService leaveQueryService;

    public LeaveRequestController(LeaveRequestService leaveRequestService,
                                  LeaveQueryService leaveQueryService) {
        this.leaveRequestService = leaveRequestService;
        this.leaveQueryService = leaveQueryService;
    }
    /** 分页查询列表。权限点见 @RequirePerm。 */

    @GetMapping
    public R<PageResult<LeaveResponse>> list(
            @RequestParam(required = false) Long empId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long size) {
        return R.ok(leaveQueryService.list(empId, status, from, to, page, size));
    }
    /** 按 ID 查询详情。权限点见 @RequirePerm。 */

    @GetMapping("/{id}")
    public R<LeaveResponse> get(@PathVariable Long id) {
        return R.ok(leaveQueryService.get(id));
    }
    /** 新建记录。权限点见 @RequirePerm；请求体经 @Valid 校验。 */

    @RequirePerm("att:leave:write")
    @PostMapping
    public R<LeaveResponse> create(@Valid @RequestBody LeaveCreateRequest req) {
        return R.ok(leaveRequestService.create(req));
    }

    @RequirePerm("att:leave:write")
    @PostMapping("/{id}/cancel")
    public R<LeaveResponse> cancel(@PathVariable Long id,
                                   @RequestBody(required = false) LeaveActionRequest req) {
        return R.ok(leaveRequestService.cancel(id, req == null ? new LeaveActionRequest(null) : req));
    }

    @RequirePerm("att:leave:write")
    @PostMapping("/{id}/revoke")
    public R<LeaveResponse> revoke(@PathVariable Long id,
                                   @Valid @RequestBody LeaveRevokeRequest req) {
        return R.ok(leaveRequestService.revoke(id, req));
    }

    @RequirePerm("att:leave:approve")
    @PostMapping("/{id}/approve")
    public R<LeaveResponse> approve(@PathVariable Long id,
                                    @RequestBody(required = false) LeaveActionRequest req) {
        return R.ok(leaveRequestService.approve(id, req == null ? new LeaveActionRequest(null) : req));
    }

    @RequirePerm("att:leave:approve")
    @PostMapping("/{id}/reject")
    public R<LeaveResponse> reject(@PathVariable Long id,
                                   @RequestBody(required = false) LeaveActionRequest req) {
        return R.ok(leaveRequestService.reject(id, req == null ? new LeaveActionRequest(null) : req));
    }
}
