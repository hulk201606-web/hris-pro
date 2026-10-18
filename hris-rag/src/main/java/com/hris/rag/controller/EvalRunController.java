package com.hris.rag.controller;

import com.hris.rag.dto.EvalRunDtos.EvalRunCreateRequest;
import com.hris.rag.dto.EvalRunDtos.EvalRunResponse;
import com.hris.rag.dto.EvalRunDtos.EvalRunUpdateRequest;
import com.hris.rag.entity.EvalRun;
import com.hris.rag.service.EvalRunService;
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
 * EvalRun 端点。
 */
@RestController
@RequestMapping("/api/v1/kb/eval-runs")
public class EvalRunController {

    private final EvalRunService service;

    public EvalRunController(EvalRunService service) {
        this.service = service;
    }

    @RequirePerm("kb:eval-run:read")
    @GetMapping
    public R<PageResult<EvalRunResponse>> list(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long size) {
        return R.ok(service.list(page, size));
    }

    @RequirePerm("kb:eval-run:read")
    @GetMapping("/{id}")
    public R<EvalRunResponse> get(@PathVariable Long id) {
        return R.ok(service.get(id));
    }

    @RequirePerm("kb:eval-run:write")
    @PostMapping
    public R<EvalRunResponse> create(@Valid @RequestBody EvalRunCreateRequest req) {
        return R.ok(service.create(req));
    }

    @RequirePerm("kb:eval-run:write")
    @PutMapping("/{id}")
    public R<EvalRunResponse> update(@PathVariable Long id, @Valid @RequestBody EvalRunUpdateRequest req) {
        return R.ok(service.update(id, req));
    }

    @RequirePerm("kb:eval-run:write")
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return R.ok();
    }
}
