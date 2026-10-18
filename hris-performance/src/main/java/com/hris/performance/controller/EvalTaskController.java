package com.hris.performance.controller;

import com.hris.performance.dto.EvalTaskDtos.EvalTaskCreateRequest;
import com.hris.performance.dto.EvalTaskDtos.EvalTaskResponse;
import com.hris.performance.dto.EvalTaskDtos.EvalTaskUpdateRequest;
import com.hris.performance.entity.EvalTask;
import com.hris.performance.service.EvalTaskService;
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
 * EvalTask 端点。
 */
@RestController
@RequestMapping("/api/v1/prf/eval-tasks")
public class EvalTaskController {

    private final EvalTaskService service;

    public EvalTaskController(EvalTaskService service) {
        this.service = service;
    }

    @RequirePerm("prf:eval-task:read")
    @GetMapping
    public R<PageResult<EvalTaskResponse>> list(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long size) {
        return R.ok(service.list(page, size));
    }

    @RequirePerm("prf:eval-task:read")
    @GetMapping("/{id}")
    public R<EvalTaskResponse> get(@PathVariable Long id) {
        return R.ok(service.get(id));
    }

    @RequirePerm("prf:eval-task:write")
    @PostMapping
    public R<EvalTaskResponse> create(@Valid @RequestBody EvalTaskCreateRequest req) {
        return R.ok(service.create(req));
    }

    @RequirePerm("prf:eval-task:write")
    @PutMapping("/{id}")
    public R<EvalTaskResponse> update(@PathVariable Long id, @Valid @RequestBody EvalTaskUpdateRequest req) {
        return R.ok(service.update(id, req));
    }

    @RequirePerm("prf:eval-task:write")
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return R.ok();
    }
}
