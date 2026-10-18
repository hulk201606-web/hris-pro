package com.hris.rag.controller;

import com.hris.rag.dto.ChunkDtos.ChunkCreateRequest;
import com.hris.rag.dto.ChunkDtos.ChunkResponse;
import com.hris.rag.dto.ChunkDtos.ChunkUpdateRequest;
import com.hris.rag.entity.Chunk;
import com.hris.rag.service.ChunkService;
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
 * Chunk 端点。
 */
@RestController
@RequestMapping("/api/v1/kb/chunks")
public class ChunkController {

    private final ChunkService service;

    public ChunkController(ChunkService service) {
        this.service = service;
    }

    @RequirePerm("kb:chunk:read")
    @GetMapping
    public R<PageResult<ChunkResponse>> list(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long size) {
        return R.ok(service.list(page, size));
    }

    @RequirePerm("kb:chunk:read")
    @GetMapping("/{id}")
    public R<ChunkResponse> get(@PathVariable Long id) {
        return R.ok(service.get(id));
    }

    @RequirePerm("kb:chunk:write")
    @PostMapping
    public R<ChunkResponse> create(@Valid @RequestBody ChunkCreateRequest req) {
        return R.ok(service.create(req));
    }

    @RequirePerm("kb:chunk:write")
    @PutMapping("/{id}")
    public R<ChunkResponse> update(@PathVariable Long id, @Valid @RequestBody ChunkUpdateRequest req) {
        return R.ok(service.update(id, req));
    }

    @RequirePerm("kb:chunk:write")
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return R.ok();
    }
}
