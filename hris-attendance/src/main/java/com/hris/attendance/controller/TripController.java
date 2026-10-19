package com.hris.attendance.controller;

import com.hris.attendance.dto.PageResult;
import com.hris.attendance.dto.TripDtos.TripActionRequest;
import com.hris.attendance.dto.TripDtos.TripCreateRequest;
import com.hris.attendance.dto.TripDtos.TripResponse;
import com.hris.attendance.service.TripService;
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
 * 出差单端点（ATT-F-017）。
 */
@RestController
@RequestMapping("/api/v1/att/trip-requests")
public class TripController {

    private final TripService tripService;

    public TripController(TripService tripService) {
        this.tripService = tripService;
    }
    /** 分页查询列表。权限点见 @RequirePerm。 */

    @GetMapping
    public R<PageResult<TripResponse>> list(
            @RequestParam(required = false) Long empId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long size) {
        return R.ok(tripService.list(empId, status, from, to, page, size));
    }
    /** 按 ID 查询详情。权限点见 @RequirePerm。 */

    @GetMapping("/{id}")
    public R<TripResponse> get(@PathVariable Long id) {
        return R.ok(tripService.get(id));
    }
    /** 新建记录。权限点见 @RequirePerm；请求体经 @Valid 校验。 */

    @RequirePerm("att:trip:write")
    @PostMapping
    public R<TripResponse> create(@Valid @RequestBody TripCreateRequest req) {
        return R.ok(tripService.create(req));
    }

    @RequirePerm("att:trip:approve")
    @PostMapping("/{id}/approve")
    public R<TripResponse> approve(@PathVariable Long id,
                                   @RequestBody(required = false) TripActionRequest req) {
        return R.ok(tripService.approve(id, req == null ? new TripActionRequest(null) : req));
    }

    @RequirePerm("att:trip:approve")
    @PostMapping("/{id}/reject")
    public R<TripResponse> reject(@PathVariable Long id,
                                  @RequestBody(required = false) TripActionRequest req) {
        return R.ok(tripService.reject(id, req == null ? new TripActionRequest(null) : req));
    }
}
