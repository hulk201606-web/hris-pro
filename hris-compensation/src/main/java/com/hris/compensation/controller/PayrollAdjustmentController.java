package com.hris.compensation.controller;

import com.hris.compensation.dto.PayrollAdjustmentDtos.PayrollAdjustmentCreateRequest;
import com.hris.compensation.dto.PayrollAdjustmentDtos.PayrollAdjustmentResponse;
import com.hris.compensation.dto.PayrollAdjustmentDtos.PayrollAdjustmentUpdateRequest;
import com.hris.compensation.entity.PayrollAdjustment;
import com.hris.compensation.service.PayrollAdjustmentService;
import com.hris.common.api.R;
import com.hris.common.dto.PageResult;
import com.hris.common.security.RequirePerm;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * PayrollAdjustment 端点。
 */
@RestController
@RequestMapping("/api/v1/cmp/payroll-adjustments")
public class PayrollAdjustmentController {

    private final PayrollAdjustmentService service;

    public PayrollAdjustmentController(PayrollAdjustmentService service) {
        this.service = service;
    }

    @RequirePerm("cmp:payroll-adjustment:read")
    @GetMapping
    public R<PageResult<PayrollAdjustmentResponse>> list(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long size) {
        return R.ok(service.list(page, size));
    }

    @RequirePerm("cmp:payroll-adjustment:read")
    @GetMapping("/{id}")
    public R<PayrollAdjustmentResponse> get(@PathVariable Long id) {
        return R.ok(service.get(id));
    }

    @RequirePerm("cmp:payroll-adjustment:write")
    @PostMapping
    public R<PayrollAdjustmentResponse> create(@Valid @RequestBody PayrollAdjustmentCreateRequest req) {
        return R.ok(service.create(req));
    }

    @RequirePerm("cmp:payroll-adjustment:write")
    @PutMapping("/{id}")
    public R<PayrollAdjustmentResponse> update(@PathVariable Long id, @Valid @RequestBody PayrollAdjustmentUpdateRequest req) {
        return R.ok(service.update(id, req));
    }

    @RequirePerm("cmp:payroll-adjustment:write")
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return R.ok();
    }
}
