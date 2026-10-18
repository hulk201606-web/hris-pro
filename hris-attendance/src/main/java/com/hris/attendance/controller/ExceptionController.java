package com.hris.attendance.controller;

import com.hris.attendance.dto.ExceptionDtos.ExceptionBatchHandleRequest;
import com.hris.attendance.dto.ExceptionDtos.ExceptionHandleRequest;
import com.hris.attendance.dto.ExceptionDtos.ExceptionHandleResult;
import com.hris.attendance.dto.ExceptionDtos.ExceptionResponse;
import com.hris.attendance.dto.PageResult;
import com.hris.attendance.service.ExceptionService;
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
 * 考勤异常工作台端点（ATT-F-020）。
 *
 */
@RestController
@RequestMapping("/api/v1/att/exceptions")
public class ExceptionController {

    private final ExceptionService exceptionService;

    public ExceptionController(ExceptionService exceptionService) {
        this.exceptionService = exceptionService;
    }

    @GetMapping
    public R<PageResult<ExceptionResponse>> list(
            @RequestParam(required = false) Long empId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String exceptionType,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long size) {
        return R.ok(exceptionService.list(empId, from, to, exceptionType, status, page, size));
    }

    @RequirePerm("att:exception:handle")
    @PostMapping("/{id}/handle")
    public R<ExceptionResponse> handle(@PathVariable Long id,
                                       @Valid @RequestBody ExceptionHandleRequest req) {
        return R.ok(exceptionService.handle(id, req));
    }

    @RequirePerm("att:exception:handle")
    @PostMapping("/batch-handle")
    public R<ExceptionHandleResult> batchHandle(@Valid @RequestBody ExceptionBatchHandleRequest req) {
        return R.ok(exceptionService.batchHandle(req));
    }
}
