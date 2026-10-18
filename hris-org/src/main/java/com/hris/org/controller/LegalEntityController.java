package com.hris.org.controller;

import com.hris.common.api.R;
import com.hris.org.dto.CatalogRequest;
import com.hris.org.dto.CatalogResponse;
import com.hris.org.service.LegalEntityService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 法人主体端点。
 */
@RestController
@RequestMapping("/api/v1/org")
public class LegalEntityController {

    private final LegalEntityService legalEntityService;

    public LegalEntityController(LegalEntityService legalEntityService) {
        this.legalEntityService = legalEntityService;
    }

    @GetMapping("/legal-entities")
    public R<List<CatalogResponse>> list() {
        return R.ok(legalEntityService.list());
    }

    @PostMapping("/legal-entities")
    public R<CatalogResponse> create(@Valid @RequestBody CatalogRequest req) {
        return R.ok(legalEntityService.create(req));
    }

    @PutMapping("/legal-entities/{id}")
    public R<CatalogResponse> update(@PathVariable Long id, @Valid @RequestBody CatalogRequest req) {
        return R.ok(legalEntityService.update(id, req));
    }
}
