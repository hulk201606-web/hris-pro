package com.hris.rag.controller;

import com.hris.common.api.R;
import com.hris.common.security.RequirePerm;
import com.hris.rag.dto.SensitiveWordDtos.SensitiveWordCreateRequest;
import com.hris.rag.dto.SensitiveWordDtos.SensitiveWordResponse;
import com.hris.rag.service.SensitiveWordService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 敏感词端点（KBD-F-017）：复用 kb 文档管理权限码（read/write）。
 */
@RestController
@RequestMapping("/api/v1/kb/sensitive-words")
public class SensitiveWordController {

    private final SensitiveWordService service;

    public SensitiveWordController(SensitiveWordService service) {
        this.service = service;
    }

    @RequirePerm("kb:document:read")
    @GetMapping
    public R<List<SensitiveWordResponse>> list() {
        return R.ok(service.list());
    }

    @RequirePerm("kb:document:write")
    @PostMapping
    public R<SensitiveWordResponse> create(@Valid @RequestBody SensitiveWordCreateRequest req) {
        return R.ok(service.create(req));
    }

    @RequirePerm("kb:document:write")
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return R.ok();
    }
}
