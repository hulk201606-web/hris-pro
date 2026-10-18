package com.hris.recruit.controller;

import com.hris.recruit.dto.JobOpeningDtos.JobOpeningCreateRequest;
import com.hris.recruit.dto.JobOpeningDtos.JobOpeningResponse;
import com.hris.recruit.dto.JobOpeningDtos.JobOpeningUpdateRequest;
import com.hris.recruit.entity.JobOpening;
import com.hris.recruit.service.JobOpeningService;
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
 * JobOpening 端点。
 */
@RestController
@RequestMapping("/api/v1/rcu/job-openings")
public class JobOpeningController {

    private final JobOpeningService service;

    public JobOpeningController(JobOpeningService service) {
        this.service = service;
    }

    @RequirePerm("rcu:job-opening:read")
    @GetMapping
    public R<PageResult<JobOpeningResponse>> list(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long size) {
        return R.ok(service.list(page, size));
    }

    @RequirePerm("rcu:job-opening:read")
    @GetMapping("/{id}")
    public R<JobOpeningResponse> get(@PathVariable Long id) {
        return R.ok(service.get(id));
    }

    @RequirePerm("rcu:job-opening:write")
    @PostMapping
    public R<JobOpeningResponse> create(@Valid @RequestBody JobOpeningCreateRequest req) {
        return R.ok(service.create(req));
    }

    @RequirePerm("rcu:job-opening:write")
    @PutMapping("/{id}")
    public R<JobOpeningResponse> update(@PathVariable Long id, @Valid @RequestBody JobOpeningUpdateRequest req) {
        return R.ok(service.update(id, req));
    }

    @RequirePerm("rcu:job-opening:write")
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return R.ok();
    }
}
