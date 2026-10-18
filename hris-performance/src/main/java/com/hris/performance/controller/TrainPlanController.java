package com.hris.performance.controller;

import com.hris.performance.dto.TrainPlanDtos.TrainPlanCreateRequest;
import com.hris.performance.dto.TrainPlanDtos.TrainPlanResponse;
import com.hris.performance.dto.TrainPlanDtos.TrainPlanUpdateRequest;
import com.hris.performance.entity.TrainPlan;
import com.hris.performance.service.TrainPlanService;
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
 * TrainPlan 端点。
 */
@RestController
@RequestMapping("/api/v1/prf/train-plans")
public class TrainPlanController {

    private final TrainPlanService service;

    public TrainPlanController(TrainPlanService service) {
        this.service = service;
    }

    @RequirePerm("prf:train-plan:read")
    @GetMapping
    public R<PageResult<TrainPlanResponse>> list(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long size) {
        return R.ok(service.list(page, size));
    }

    @RequirePerm("prf:train-plan:read")
    @GetMapping("/{id}")
    public R<TrainPlanResponse> get(@PathVariable Long id) {
        return R.ok(service.get(id));
    }

    @RequirePerm("prf:train-plan:write")
    @PostMapping
    public R<TrainPlanResponse> create(@Valid @RequestBody TrainPlanCreateRequest req) {
        return R.ok(service.create(req));
    }

    @RequirePerm("prf:train-plan:write")
    @PutMapping("/{id}")
    public R<TrainPlanResponse> update(@PathVariable Long id, @Valid @RequestBody TrainPlanUpdateRequest req) {
        return R.ok(service.update(id, req));
    }

    @RequirePerm("prf:train-plan:write")
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return R.ok();
    }
}
