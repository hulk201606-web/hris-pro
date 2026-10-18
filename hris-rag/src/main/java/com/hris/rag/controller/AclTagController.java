package com.hris.rag.controller;

import com.hris.rag.dto.AclTagDtos.AclTagCreateRequest;
import com.hris.rag.dto.AclTagDtos.AclTagResponse;
import com.hris.rag.dto.AclTagDtos.AclTagUpdateRequest;
import com.hris.rag.entity.AclTag;
import com.hris.rag.service.AclTagService;
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
 * AclTag 端点。
 */
@RestController
@RequestMapping("/api/v1/kb/acl-tags")
public class AclTagController {

    private final AclTagService service;

    public AclTagController(AclTagService service) {
        this.service = service;
    }

    @RequirePerm("kb:acl-tag:read")
    @GetMapping
    public R<PageResult<AclTagResponse>> list(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long size) {
        return R.ok(service.list(page, size));
    }

    @RequirePerm("kb:acl-tag:read")
    @GetMapping("/{id}")
    public R<AclTagResponse> get(@PathVariable Long id) {
        return R.ok(service.get(id));
    }

    @RequirePerm("kb:acl-tag:write")
    @PostMapping
    public R<AclTagResponse> create(@Valid @RequestBody AclTagCreateRequest req) {
        return R.ok(service.create(req));
    }

    @RequirePerm("kb:acl-tag:write")
    @PutMapping("/{id}")
    public R<AclTagResponse> update(@PathVariable Long id, @Valid @RequestBody AclTagUpdateRequest req) {
        return R.ok(service.update(id, req));
    }

    @RequirePerm("kb:acl-tag:write")
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return R.ok();
    }
}
