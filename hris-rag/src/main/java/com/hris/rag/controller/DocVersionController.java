package com.hris.rag.controller;

import com.hris.rag.dto.DocVersionDtos.DocVersionCreateRequest;
import com.hris.rag.dto.DocVersionDtos.DocVersionResponse;
import com.hris.rag.dto.DocVersionDtos.DocVersionUpdateRequest;
import com.hris.rag.entity.DocVersion;
import com.hris.rag.service.DocVersionService;
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
 * DocVersion 端点。
 */
@RestController
@RequestMapping("/api/v1/kb/doc-versions")
public class DocVersionController {

    private final DocVersionService service;

    public DocVersionController(DocVersionService service) {
        this.service = service;
    }

    @RequirePerm("kb:doc-version:read")
    @GetMapping
    public R<PageResult<DocVersionResponse>> list(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long size) {
        return R.ok(service.list(page, size));
    }

    @RequirePerm("kb:doc-version:read")
    @GetMapping("/{id}")
    public R<DocVersionResponse> get(@PathVariable Long id) {
        return R.ok(service.get(id));
    }

    @RequirePerm("kb:doc-version:write")
    @PostMapping
    public R<DocVersionResponse> create(@Valid @RequestBody DocVersionCreateRequest req) {
        return R.ok(service.create(req));
    }

    @RequirePerm("kb:doc-version:write")
    @PutMapping("/{id}")
    public R<DocVersionResponse> update(@PathVariable Long id, @Valid @RequestBody DocVersionUpdateRequest req) {
        return R.ok(service.update(id, req));
    }

    @RequirePerm("kb:doc-version:write")
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return R.ok();
    }
}
