package com.hris.org.controller;

import com.hris.common.api.R;
import com.hris.common.security.RequirePerm;
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

    /** 查询全部法人主体（组织/编制/雇佣关系的法定承载实体，默认 CNY 币种）。 */
    @RequirePerm("org:legal-entity:read")
    @GetMapping("/legal-entities")
    public R<List<CatalogResponse>> list() {
        return R.ok(legalEntityService.list());
    }

    /** 新建法人主体：统一社会信用代码自动转大写，默认状态有效、币种 CNY。 */
    @RequirePerm("org:legal-entity:write")
    @PostMapping("/legal-entities")
    public R<CatalogResponse> create(@Valid @RequestBody CatalogRequest req) {
        return R.ok(legalEntityService.create(req));
    }

    /** 更新法人主体：仅允许改名称与启停状态；USCC/币种 MVP 不提供变更。 */
    @RequirePerm("org:legal-entity:write")
    @PutMapping("/legal-entities/{id}")
    public R<CatalogResponse> update(@PathVariable Long id, @Valid @RequestBody CatalogRequest req) {
        return R.ok(legalEntityService.update(id, req));
    }
}
