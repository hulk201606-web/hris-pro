package com.hris.org.controller;

import com.hris.common.api.R;
import com.hris.org.dto.CatalogRequest;
import com.hris.org.dto.CatalogResponse;
import com.hris.org.service.JobService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 职务端点。
 */
@RestController
@RequestMapping("/api/v1/org")
public class JobController {

    private final JobService jobService;

    public JobController(JobService jobService) {
        this.jobService = jobService;
    }

    @GetMapping("/jobs")
    public R<List<CatalogResponse>> list() {
        return R.ok(jobService.list());
    }

    @PostMapping("/jobs")
    public R<CatalogResponse> create(@Valid @RequestBody CatalogRequest req) {
        return R.ok(jobService.create(req));
    }

    @PutMapping("/jobs/{id}")
    public R<CatalogResponse> update(@PathVariable Long id, @Valid @RequestBody CatalogRequest req) {
        return R.ok(jobService.update(id, req));
    }

    @DeleteMapping("/jobs/{id}")
    public R<Void> delete(@PathVariable Long id) {
        jobService.delete(id);
        return R.ok();
    }
}
