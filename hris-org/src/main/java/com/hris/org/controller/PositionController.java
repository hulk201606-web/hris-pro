package com.hris.org.controller;

import com.hris.common.api.R;
import com.hris.org.dto.CatalogRequest;
import com.hris.org.dto.CatalogResponse;
import com.hris.org.service.PositionService;
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
 * 岗位端点。
 */
@RestController
@RequestMapping("/api/v1/org")
public class PositionController {

    private final PositionService positionService;

    public PositionController(PositionService positionService) {
        this.positionService = positionService;
    }

    @GetMapping("/positions")
    public R<List<CatalogResponse>> list() {
        return R.ok(positionService.list());
    }

    @PostMapping("/positions")
    public R<CatalogResponse> create(@Valid @RequestBody CatalogRequest req) {
        return R.ok(positionService.create(req));
    }

    @PutMapping("/positions/{id}")
    public R<CatalogResponse> update(@PathVariable Long id, @Valid @RequestBody CatalogRequest req) {
        return R.ok(positionService.update(id, req));
    }

    @DeleteMapping("/positions/{id}")
    public R<Void> delete(@PathVariable Long id) {
        positionService.delete(id);
        return R.ok();
    }
}
