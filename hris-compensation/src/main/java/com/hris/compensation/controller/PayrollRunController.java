package com.hris.compensation.controller;

import com.hris.compensation.dto.PayrollRunDtos.PayrollRunCreateRequest;
import com.hris.compensation.dto.PayrollRunDtos.PayrollRunResponse;
import com.hris.compensation.dto.PayrollRunDtos.PayrollRunUpdateRequest;
import com.hris.compensation.entity.PayrollRun;
import com.hris.compensation.service.PayrollRunService;
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
 * PayrollRun 端点。
 */
@RestController
@RequestMapping("/api/v1/cmp/payroll-runs")
public class PayrollRunController {

    private final PayrollRunService service;

    public PayrollRunController(PayrollRunService service) {
        this.service = service;
    }

    @RequirePerm("cmp:payroll-run:read")
    @GetMapping
    public R<PageResult<PayrollRunResponse>> list(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long size) {
        return R.ok(service.list(page, size));
    }

    @RequirePerm("cmp:payroll-run:read")
    @GetMapping("/{id}")
    public R<PayrollRunResponse> get(@PathVariable Long id) {
        return R.ok(service.get(id));
    }

    @RequirePerm("cmp:payroll-run:write")
    @PostMapping
    public R<PayrollRunResponse> create(@Valid @RequestBody PayrollRunCreateRequest req) {
        return R.ok(service.create(req));
    }

    @RequirePerm("cmp:payroll-run:write")
    @PutMapping("/{id}")
    public R<PayrollRunResponse> update(@PathVariable Long id, @Valid @RequestBody PayrollRunUpdateRequest req) {
        return R.ok(service.update(id, req));
    }

    @RequirePerm("cmp:payroll-run:write")
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return R.ok();
    }
}
