package com.hris.rag.controller;

import com.hris.rag.dto.EvalCaseDtos.EvalCaseCreateRequest;
import com.hris.rag.dto.EvalCaseDtos.EvalCaseResponse;
import com.hris.rag.dto.EvalCaseDtos.EvalCaseUpdateRequest;
import com.hris.rag.entity.EvalCase;
import com.hris.rag.service.EvalCaseService;
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
 * EvalCase 端点。
 */
@RestController
@RequestMapping("/api/v1/kb/eval-cases")
public class EvalCaseController {

    private final EvalCaseService service;

    public EvalCaseController(EvalCaseService service) {
        this.service = service;
    }

    @RequirePerm("kb:eval-case:read")
    @GetMapping
    public R<PageResult<EvalCaseResponse>> list(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long size) {
        return R.ok(service.list(page, size));
    }

    @RequirePerm("kb:eval-case:read")
    @GetMapping("/{id}")
    public R<EvalCaseResponse> get(@PathVariable Long id) {
        return R.ok(service.get(id));
    }

    @RequirePerm("kb:eval-case:write")
    @PostMapping
    public R<EvalCaseResponse> create(@Valid @RequestBody EvalCaseCreateRequest req) {
        return R.ok(service.create(req));
    }

    @RequirePerm("kb:eval-case:write")
    @PutMapping("/{id}")
    public R<EvalCaseResponse> update(@PathVariable Long id, @Valid @RequestBody EvalCaseUpdateRequest req) {
        return R.ok(service.update(id, req));
    }

    @RequirePerm("kb:eval-case:write")
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return R.ok();
    }
}
