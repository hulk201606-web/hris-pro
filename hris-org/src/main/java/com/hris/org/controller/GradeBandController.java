package com.hris.org.controller;

import com.hris.common.api.R;
import com.hris.org.dto.CatalogRequest;
import com.hris.org.dto.CatalogResponse;
import com.hris.org.service.GradeBandService;
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
 * 职级带端点。
 */
@RestController
@RequestMapping("/api/v1/org")
public class GradeBandController {

    private final GradeBandService gradeBandService;

    public GradeBandController(GradeBandService gradeBandService) {
        this.gradeBandService = gradeBandService;
    }

    @GetMapping("/bands")
    public R<List<CatalogResponse>> list() {
        return R.ok(gradeBandService.list());
    }

    @PostMapping("/bands")
    public R<CatalogResponse> create(@Valid @RequestBody CatalogRequest req) {
        return R.ok(gradeBandService.create(req));
    }

    @PutMapping("/bands/{id}")
    public R<CatalogResponse> update(@PathVariable Long id, @Valid @RequestBody CatalogRequest req) {
        return R.ok(gradeBandService.update(id, req));
    }

    @DeleteMapping("/bands/{id}")
    public R<Void> delete(@PathVariable Long id) {
        gradeBandService.delete(id);
        return R.ok();
    }
}
