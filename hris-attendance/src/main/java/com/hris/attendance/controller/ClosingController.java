package com.hris.attendance.controller;

import com.hris.attendance.dto.ClosingDtos.AdjustmentCreateRequest;
import com.hris.attendance.dto.ClosingDtos.AdjustmentResponse;
import com.hris.attendance.dto.ClosingDtos.ClosingDetailResponse;
import com.hris.attendance.dto.ClosingDtos.ClosingResponse;
import com.hris.attendance.dto.ClosingDtos.ClosingRunRequest;
import com.hris.attendance.service.MonthlyClosingService;
import com.hris.common.api.R;
import com.hris.common.security.RequirePerm;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 考勤月结与调整端点（ATT-F-023 / ATT-F-024）。
 */
@RestController
@RequestMapping("/api/v1/att/closings")
public class ClosingController {

    private final MonthlyClosingService monthlyClosingService;

    public ClosingController(MonthlyClosingService monthlyClosingService) {
        this.monthlyClosingService = monthlyClosingService;
    }

    @GetMapping
    public R<List<ClosingResponse>> list() {
        return R.ok(monthlyClosingService.list());
    }

    @GetMapping("/{periodCode}")
    public R<ClosingDetailResponse> detail(@PathVariable String periodCode) {
        return R.ok(monthlyClosingService.detail(periodCode));
    }

    @GetMapping("/adjustments")
    public R<List<AdjustmentResponse>> adjustments(@RequestParam(required = false) Long closingId) {
        return R.ok(monthlyClosingService.adjustments(closingId));
    }

    @RequirePerm("att:closing:run")
    @PostMapping("/run")
    public R<ClosingResponse> run(@Valid @RequestBody ClosingRunRequest req) {
        return R.ok(monthlyClosingService.run(req));
    }

    @RequirePerm("att:closing:adjust")
    @PostMapping("/adjustments")
    public R<AdjustmentResponse> addAdjustment(@Valid @RequestBody AdjustmentCreateRequest req) {
        return R.ok(monthlyClosingService.addAdjustment(req));
    }
}
