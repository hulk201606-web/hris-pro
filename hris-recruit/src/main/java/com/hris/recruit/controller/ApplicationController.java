package com.hris.recruit.controller;

import com.hris.recruit.dto.ApplicationDtos.ApplicationCreateRequest;
import com.hris.recruit.dto.ApplicationDtos.ApplicationResponse;
import com.hris.recruit.dto.ApplicationDtos.ApplicationUpdateRequest;
import com.hris.recruit.entity.Application;
import com.hris.recruit.service.ApplicationService;
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
 * Application 端点。
 */
@RestController
@RequestMapping("/api/v1/rcu/applications")
public class ApplicationController {

    private final ApplicationService service;

    public ApplicationController(ApplicationService service) {
        this.service = service;
    }

    @RequirePerm("rcu:application:read")
    @GetMapping
    public R<PageResult<ApplicationResponse>> list(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long size) {
        return R.ok(service.list(page, size));
    }

    @RequirePerm("rcu:application:read")
    @GetMapping("/{id}")
    public R<ApplicationResponse> get(@PathVariable Long id) {
        return R.ok(service.get(id));
    }

    @RequirePerm("rcu:application:write")
    @PostMapping
    public R<ApplicationResponse> create(@Valid @RequestBody ApplicationCreateRequest req) {
        return R.ok(service.create(req));
    }

    @RequirePerm("rcu:application:write")
    @PutMapping("/{id}")
    public R<ApplicationResponse> update(@PathVariable Long id, @Valid @RequestBody ApplicationUpdateRequest req) {
        return R.ok(service.update(id, req));
    }

    @RequirePerm("rcu:application:write")
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return R.ok();
    }
}
