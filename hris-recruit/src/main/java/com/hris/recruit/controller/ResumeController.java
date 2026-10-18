package com.hris.recruit.controller;

import com.hris.recruit.dto.ResumeDtos.ResumeCreateRequest;
import com.hris.recruit.dto.ResumeDtos.ResumeResponse;
import com.hris.recruit.dto.ResumeDtos.ResumeUpdateRequest;
import com.hris.recruit.entity.Resume;
import com.hris.recruit.service.ResumeService;
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
 * Resume 端点。
 */
@RestController
@RequestMapping("/api/v1/rcu/resumes")
public class ResumeController {

    private final ResumeService service;

    public ResumeController(ResumeService service) {
        this.service = service;
    }

    @RequirePerm("rcu:resume:read")
    @GetMapping
    public R<PageResult<ResumeResponse>> list(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long size) {
        return R.ok(service.list(page, size));
    }

    @RequirePerm("rcu:resume:read")
    @GetMapping("/{id}")
    public R<ResumeResponse> get(@PathVariable Long id) {
        return R.ok(service.get(id));
    }

    @RequirePerm("rcu:resume:write")
    @PostMapping
    public R<ResumeResponse> create(@Valid @RequestBody ResumeCreateRequest req) {
        return R.ok(service.create(req));
    }

    @RequirePerm("rcu:resume:write")
    @PutMapping("/{id}")
    public R<ResumeResponse> update(@PathVariable Long id, @Valid @RequestBody ResumeUpdateRequest req) {
        return R.ok(service.update(id, req));
    }

    @RequirePerm("rcu:resume:write")
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return R.ok();
    }
}
