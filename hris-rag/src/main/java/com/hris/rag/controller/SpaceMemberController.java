package com.hris.rag.controller;

import com.hris.rag.dto.SpaceMemberDtos.SpaceMemberCreateRequest;
import com.hris.rag.dto.SpaceMemberDtos.SpaceMemberResponse;
import com.hris.rag.dto.SpaceMemberDtos.SpaceMemberUpdateRequest;
import com.hris.rag.entity.SpaceMember;
import com.hris.rag.service.SpaceMemberService;
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
 * SpaceMember 端点。
 */
@RestController
@RequestMapping("/api/v1/kb/space-members")
public class SpaceMemberController {

    private final SpaceMemberService service;

    public SpaceMemberController(SpaceMemberService service) {
        this.service = service;
    }

    @RequirePerm("kb:space-member:read")
    @GetMapping
    public R<PageResult<SpaceMemberResponse>> list(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long size) {
        return R.ok(service.list(page, size));
    }

    @RequirePerm("kb:space-member:read")
    @GetMapping("/{id}")
    public R<SpaceMemberResponse> get(@PathVariable Long id) {
        return R.ok(service.get(id));
    }

    @RequirePerm("kb:space-member:write")
    @PostMapping
    public R<SpaceMemberResponse> create(@Valid @RequestBody SpaceMemberCreateRequest req) {
        return R.ok(service.create(req));
    }

    @RequirePerm("kb:space-member:write")
    @PutMapping("/{id}")
    public R<SpaceMemberResponse> update(@PathVariable Long id, @Valid @RequestBody SpaceMemberUpdateRequest req) {
        return R.ok(service.update(id, req));
    }

    @RequirePerm("kb:space-member:write")
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return R.ok();
    }
}
