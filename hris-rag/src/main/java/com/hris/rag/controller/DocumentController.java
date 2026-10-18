package com.hris.rag.controller;

import com.hris.rag.dto.DocumentDtos.DocumentCreateRequest;
import com.hris.rag.dto.DocumentDtos.DocumentResponse;
import com.hris.rag.dto.DocumentDtos.DocumentUpdateRequest;
import com.hris.rag.entity.Document;
import com.hris.rag.service.DocumentService;
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
 * Document 端点。
 */
@RestController
@RequestMapping("/api/v1/kb/documents")
public class DocumentController {

    private final DocumentService service;

    public DocumentController(DocumentService service) {
        this.service = service;
    }

    @RequirePerm("kb:document:read")
    @GetMapping
    public R<PageResult<DocumentResponse>> list(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long size) {
        return R.ok(service.list(page, size));
    }

    @RequirePerm("kb:document:read")
    @GetMapping("/{id}")
    public R<DocumentResponse> get(@PathVariable Long id) {
        return R.ok(service.get(id));
    }

    @RequirePerm("kb:document:write")
    @PostMapping
    public R<DocumentResponse> create(@Valid @RequestBody DocumentCreateRequest req) {
        return R.ok(service.create(req));
    }

    @RequirePerm("kb:document:write")
    @PutMapping("/{id}")
    public R<DocumentResponse> update(@PathVariable Long id, @Valid @RequestBody DocumentUpdateRequest req) {
        return R.ok(service.update(id, req));
    }

    @RequirePerm("kb:document:write")
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return R.ok();
    }
}
