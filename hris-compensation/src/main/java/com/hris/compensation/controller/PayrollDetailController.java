package com.hris.compensation.controller;

import com.hris.compensation.dto.PayrollDetailDtos.PayrollDetailCreateRequest;
import com.hris.compensation.dto.PayrollDetailDtos.PayrollDetailResponse;
import com.hris.compensation.dto.PayrollDetailDtos.PayrollDetailUpdateRequest;
import com.hris.compensation.entity.PayrollDetail;
import com.hris.compensation.service.PayrollDetailService;
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
 * PayrollDetail 端点。
 */
@RestController
@RequestMapping("/api/v1/cmp/payroll-details")
public class PayrollDetailController {

    private final PayrollDetailService service;

    public PayrollDetailController(PayrollDetailService service) {
        this.service = service;
    }

    @RequirePerm("cmp:payroll-detail:read")
    @GetMapping
    public R<PageResult<PayrollDetailResponse>> list(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long size) {
        return R.ok(service.list(page, size));
    }

    @RequirePerm("cmp:payroll-detail:read")
    @GetMapping("/{id}")
    public R<PayrollDetailResponse> get(@PathVariable Long id) {
        return R.ok(service.get(id));
    }

    @RequirePerm("cmp:payroll-detail:write")
    @PostMapping
    public R<PayrollDetailResponse> create(@Valid @RequestBody PayrollDetailCreateRequest req) {
        return R.ok(service.create(req));
    }

    @RequirePerm("cmp:payroll-detail:write")
    @PutMapping("/{id}")
    public R<PayrollDetailResponse> update(@PathVariable Long id, @Valid @RequestBody PayrollDetailUpdateRequest req) {
        return R.ok(service.update(id, req));
    }

    @RequirePerm("cmp:payroll-detail:write")
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return R.ok();
    }
}
