package com.hris.compensation.controller;

import com.hris.compensation.dto.PayGradeDtos.PayGradeCreateRequest;
import com.hris.compensation.dto.PayGradeDtos.PayGradeResponse;
import com.hris.compensation.dto.PayGradeDtos.PayGradeUpdateRequest;
import com.hris.compensation.entity.PayGrade;
import com.hris.compensation.service.PayGradeService;
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
 * PayGrade 端点。
 */
@RestController
@RequestMapping("/api/v1/cmp/pay-grades")
public class PayGradeController {

    private final PayGradeService service;

    public PayGradeController(PayGradeService service) {
        this.service = service;
    }

    @RequirePerm("cmp:pay-grade:read")
    @GetMapping
    public R<PageResult<PayGradeResponse>> list(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long size) {
        return R.ok(service.list(page, size));
    }

    @RequirePerm("cmp:pay-grade:read")
    @GetMapping("/{id}")
    public R<PayGradeResponse> get(@PathVariable Long id) {
        return R.ok(service.get(id));
    }

    @RequirePerm("cmp:pay-grade:write")
    @PostMapping
    public R<PayGradeResponse> create(@Valid @RequestBody PayGradeCreateRequest req) {
        return R.ok(service.create(req));
    }

    @RequirePerm("cmp:pay-grade:write")
    @PutMapping("/{id}")
    public R<PayGradeResponse> update(@PathVariable Long id, @Valid @RequestBody PayGradeUpdateRequest req) {
        return R.ok(service.update(id, req));
    }

    @RequirePerm("cmp:pay-grade:write")
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return R.ok();
    }
}
