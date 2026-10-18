package com.hris.performance.controller;

import com.hris.performance.dto.OkrObjectiveDtos.OkrObjectiveCreateRequest;
import com.hris.performance.dto.OkrObjectiveDtos.OkrObjectiveResponse;
import com.hris.performance.dto.OkrObjectiveDtos.OkrObjectiveUpdateRequest;
import com.hris.performance.entity.OkrObjective;
import com.hris.performance.service.OkrObjectiveService;
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
 * OkrObjective 端点。
 */
@RestController
@RequestMapping("/api/v1/prf/okr-objectives")
public class OkrObjectiveController {

    private final OkrObjectiveService service;

    public OkrObjectiveController(OkrObjectiveService service) {
        this.service = service;
    }

    @RequirePerm("prf:okr-objective:read")
    @GetMapping
    public R<PageResult<OkrObjectiveResponse>> list(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long size) {
        return R.ok(service.list(page, size));
    }

    @RequirePerm("prf:okr-objective:read")
    @GetMapping("/{id}")
    public R<OkrObjectiveResponse> get(@PathVariable Long id) {
        return R.ok(service.get(id));
    }

    @RequirePerm("prf:okr-objective:write")
    @PostMapping
    public R<OkrObjectiveResponse> create(@Valid @RequestBody OkrObjectiveCreateRequest req) {
        return R.ok(service.create(req));
    }

    @RequirePerm("prf:okr-objective:write")
    @PutMapping("/{id}")
    public R<OkrObjectiveResponse> update(@PathVariable Long id, @Valid @RequestBody OkrObjectiveUpdateRequest req) {
        return R.ok(service.update(id, req));
    }

    @RequirePerm("prf:okr-objective:write")
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return R.ok();
    }
}
