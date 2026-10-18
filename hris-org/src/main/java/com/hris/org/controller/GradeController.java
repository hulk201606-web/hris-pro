package com.hris.org.controller;

import com.hris.common.api.R;
import com.hris.org.dto.CatalogRequest;
import com.hris.org.dto.CatalogResponse;
import com.hris.org.service.GradeService;
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
 * 职级端点。
 */
@RestController
@RequestMapping("/api/v1/org")
public class GradeController {

    private final GradeService gradeService;

    public GradeController(GradeService gradeService) {
        this.gradeService = gradeService;
    }

    @GetMapping("/grades")
    public R<List<CatalogResponse>> list() {
        return R.ok(gradeService.list());
    }

    @PostMapping("/grades")
    public R<CatalogResponse> create(@Valid @RequestBody CatalogRequest req) {
        return R.ok(gradeService.create(req));
    }

    @PutMapping("/grades/{id}")
    public R<CatalogResponse> update(@PathVariable Long id, @Valid @RequestBody CatalogRequest req) {
        return R.ok(gradeService.update(id, req));
    }

    @DeleteMapping("/grades/{id}")
    public R<Void> delete(@PathVariable Long id) {
        gradeService.delete(id);
        return R.ok();
    }
}
