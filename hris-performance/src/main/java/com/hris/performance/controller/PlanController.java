package com.hris.performance.controller;

import com.hris.performance.dto.PlanDtos.PlanCreateRequest;
import com.hris.performance.dto.PlanDtos.PlanResponse;
import com.hris.performance.dto.PlanDtos.PlanUpdateRequest;
import com.hris.performance.entity.Plan;
import com.hris.performance.service.PlanService;
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
 * Plan 端点。
 */
@RestController
@RequestMapping("/api/v1/prf/plans")
public class PlanController {

    private final PlanService service;

    public PlanController(PlanService service) {
        this.service = service;
    }

    @RequirePerm("prf:plan:read")
    @GetMapping
    public R<PageResult<PlanResponse>> list(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long size) {
        return R.ok(service.list(page, size));
    }

    @RequirePerm("prf:plan:read")
    @GetMapping("/{id}")
    public R<PlanResponse> get(@PathVariable Long id) {
        return R.ok(service.get(id));
    }

    @RequirePerm("prf:plan:write")
    @PostMapping
    public R<PlanResponse> create(@Valid @RequestBody PlanCreateRequest req) {
        return R.ok(service.create(req));
    }

    @RequirePerm("prf:plan:write")
    @PutMapping("/{id}")
    public R<PlanResponse> update(@PathVariable Long id, @Valid @RequestBody PlanUpdateRequest req) {
        return R.ok(service.update(id, req));
    }

    @RequirePerm("prf:plan:write")
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return R.ok();
    }
}
