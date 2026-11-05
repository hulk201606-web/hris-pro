package com.hris.attendance.controller;

import com.hris.attendance.dto.LeaveQuotaDtos.LeaveLedgerResponse;
import com.hris.attendance.dto.PageResult;
import com.hris.attendance.service.LeaveQuotaService;
import com.hris.common.api.R;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

/**
 * 假勤统一台账端点（批11#20）：前端「额度/台账」页共用契约。
 *
 * <p>与 {@code GET /api/v1/att/leave-quotas/ledgers}（不分页、按员工/假种过滤）并存：
 * 本端点补分页与时间区间，供列表页直接消费；口径完全一致（同一 service 查询条件）。</p>
 */
@RestController
@RequestMapping("/api/v1/att/ledgers")
public class LedgerController {

    private final LeaveQuotaService leaveQuotaService;

    public LedgerController(LeaveQuotaService leaveQuotaService) {
        this.leaveQuotaService = leaveQuotaService;
    }

    /** 额度流水分页查询：from/to 按流水创建时间（UTC）过滤。 */
    @GetMapping
    public R<PageResult<LeaveLedgerResponse>> list(
            @RequestParam(required = false) Long empId,
            @RequestParam(required = false) Long leaveTypeId,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long size) {
        return R.ok(leaveQuotaService.ledgerPage(empId, leaveTypeId, from, to, page, size));
    }
}
