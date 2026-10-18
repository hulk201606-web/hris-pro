package com.hris.rag.controller;

import com.hris.rag.dto.EvalSetDtos.EvalSetCreateRequest;
import com.hris.rag.dto.EvalSetDtos.EvalSetResponse;
import com.hris.rag.dto.EvalSetDtos.EvalSetUpdateRequest;
import com.hris.rag.entity.EvalSet;
import com.hris.rag.service.EvalSetService;
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
 * EvalSet 端点。
 */
@RestController
@RequestMapping("/api/v1/kb/eval-sets")
public class EvalSetController {

    private final EvalSetService service;

    public EvalSetController(EvalSetService service) {
        this.service = service;
    }

    @RequirePerm("kb:eval-set:read")
    @GetMapping
    public R<PageResult<EvalSetResponse>> list(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long size) {
        return R.ok(service.list(page, size));
    }

    @RequirePerm("kb:eval-set:read")
    @GetMapping("/{id}")
    public R<EvalSetResponse> get(@PathVariable Long id) {
        return R.ok(service.get(id));
    }

    @RequirePerm("kb:eval-set:write")
    @PostMapping
    public R<EvalSetResponse> create(@Valid @RequestBody EvalSetCreateRequest req) {
        return R.ok(service.create(req));
    }

    @RequirePerm("kb:eval-set:write")
    @PutMapping("/{id}")
    public R<EvalSetResponse> update(@PathVariable Long id, @Valid @RequestBody EvalSetUpdateRequest req) {
        return R.ok(service.update(id, req));
    }

    @RequirePerm("kb:eval-set:write")
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return R.ok();
    }
}
