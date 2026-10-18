package com.hris.performance.controller;

import com.hris.performance.dto.CourseDtos.CourseCreateRequest;
import com.hris.performance.dto.CourseDtos.CourseResponse;
import com.hris.performance.dto.CourseDtos.CourseUpdateRequest;
import com.hris.performance.entity.Course;
import com.hris.performance.service.CourseService;
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
 * Course 端点。
 */
@RestController
@RequestMapping("/api/v1/prf/courses")
public class CourseController {

    private final CourseService service;

    public CourseController(CourseService service) {
        this.service = service;
    }

    @RequirePerm("prf:course:read")
    @GetMapping
    public R<PageResult<CourseResponse>> list(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long size) {
        return R.ok(service.list(page, size));
    }

    @RequirePerm("prf:course:read")
    @GetMapping("/{id}")
    public R<CourseResponse> get(@PathVariable Long id) {
        return R.ok(service.get(id));
    }

    @RequirePerm("prf:course:write")
    @PostMapping
    public R<CourseResponse> create(@Valid @RequestBody CourseCreateRequest req) {
        return R.ok(service.create(req));
    }

    @RequirePerm("prf:course:write")
    @PutMapping("/{id}")
    public R<CourseResponse> update(@PathVariable Long id, @Valid @RequestBody CourseUpdateRequest req) {
        return R.ok(service.update(id, req));
    }

    @RequirePerm("prf:course:write")
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return R.ok();
    }
}
