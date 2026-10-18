package com.hris.rag.controller;

import com.hris.rag.dto.SpaceDtos.SpaceCreateRequest;
import com.hris.rag.dto.SpaceDtos.SpaceResponse;
import com.hris.rag.dto.SpaceDtos.SpaceUpdateRequest;
import com.hris.rag.entity.Space;
import com.hris.rag.service.SpaceService;
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
 * Space 端点。
 */
@RestController
@RequestMapping("/api/v1/kb/spaces")
public class SpaceController {

    private final SpaceService service;

    public SpaceController(SpaceService service) {
        this.service = service;
    }

    @RequirePerm("kb:space:read")
    @GetMapping
    public R<PageResult<SpaceResponse>> list(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long size) {
        return R.ok(service.list(page, size));
    }

    @RequirePerm("kb:space:read")
    @GetMapping("/{id}")
    public R<SpaceResponse> get(@PathVariable Long id) {
        return R.ok(service.get(id));
    }

    @RequirePerm("kb:space:write")
    @PostMapping
    public R<SpaceResponse> create(@Valid @RequestBody SpaceCreateRequest req) {
        return R.ok(service.create(req));
    }

    @RequirePerm("kb:space:write")
    @PutMapping("/{id}")
    public R<SpaceResponse> update(@PathVariable Long id, @Valid @RequestBody SpaceUpdateRequest req) {
        return R.ok(service.update(id, req));
    }

    @RequirePerm("kb:space:write")
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return R.ok();
    }
}
