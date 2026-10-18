package com.hris.performance.controller;

import com.hris.performance.dto.InstructorDtos.InstructorCreateRequest;
import com.hris.performance.dto.InstructorDtos.InstructorResponse;
import com.hris.performance.dto.InstructorDtos.InstructorUpdateRequest;
import com.hris.performance.entity.Instructor;
import com.hris.performance.service.InstructorService;
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
 * Instructor 端点。
 */
@RestController
@RequestMapping("/api/v1/prf/instructors")
public class InstructorController {

    private final InstructorService service;

    public InstructorController(InstructorService service) {
        this.service = service;
    }

    @RequirePerm("prf:instructor:read")
    @GetMapping
    public R<PageResult<InstructorResponse>> list(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long size) {
        return R.ok(service.list(page, size));
    }

    @RequirePerm("prf:instructor:read")
    @GetMapping("/{id}")
    public R<InstructorResponse> get(@PathVariable Long id) {
        return R.ok(service.get(id));
    }

    @RequirePerm("prf:instructor:write")
    @PostMapping
    public R<InstructorResponse> create(@Valid @RequestBody InstructorCreateRequest req) {
        return R.ok(service.create(req));
    }

    @RequirePerm("prf:instructor:write")
    @PutMapping("/{id}")
    public R<InstructorResponse> update(@PathVariable Long id, @Valid @RequestBody InstructorUpdateRequest req) {
        return R.ok(service.update(id, req));
    }

    @RequirePerm("prf:instructor:write")
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return R.ok();
    }
}
