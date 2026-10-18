package com.hris.attendance.controller;

import com.hris.attendance.dto.PageResult;
import com.hris.attendance.dto.ShiftAssignmentDtos.AssignmentBatchRequest;
import com.hris.attendance.dto.ShiftAssignmentDtos.AssignmentBatchResult;
import com.hris.attendance.dto.ShiftAssignmentDtos.AssignmentCycleRequest;
import com.hris.attendance.dto.ShiftAssignmentDtos.AssignmentResponse;
import com.hris.attendance.service.ShiftAssignmentService;
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

import java.time.LocalDate;

/**
 * 排班端点（ATT-F-003）：批量排班与周期模板。
 */
@RestController
@RequestMapping("/api/v1/att/assignments")
public class ShiftAssignmentController {

    private final ShiftAssignmentService shiftAssignmentService;

    public ShiftAssignmentController(ShiftAssignmentService shiftAssignmentService) {
        this.shiftAssignmentService = shiftAssignmentService;
    }

    @GetMapping
    public R<PageResult<AssignmentResponse>> query(
            @RequestParam(required = false) Long empId,
            @RequestParam(required = false) Long shiftId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long size) {
        return R.ok(shiftAssignmentService.query(empId, shiftId, from, to, page, size));
    }

    @RequirePerm("att:shift:assign")
    @PostMapping("/batch")
    public R<AssignmentBatchResult> batch(@Valid @RequestBody AssignmentBatchRequest req) {
        return R.ok(shiftAssignmentService.batch(req));
    }

    @RequirePerm("att:shift:assign")
    @PostMapping("/cycle")
    public R<AssignmentBatchResult> cycle(@Valid @RequestBody AssignmentCycleRequest req) {
        return R.ok(shiftAssignmentService.cycle(req));
    }
}
