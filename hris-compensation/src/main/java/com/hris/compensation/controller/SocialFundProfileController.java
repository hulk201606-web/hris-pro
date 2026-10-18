package com.hris.compensation.controller;

import com.hris.compensation.dto.SocialFundProfileDtos.SocialFundProfileCreateRequest;
import com.hris.compensation.dto.SocialFundProfileDtos.SocialFundProfileResponse;
import com.hris.compensation.dto.SocialFundProfileDtos.SocialFundProfileUpdateRequest;
import com.hris.compensation.entity.SocialFundProfile;
import com.hris.compensation.service.SocialFundProfileService;
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
 * SocialFundProfile 端点。
 */
@RestController
@RequestMapping("/api/v1/cmp/social-fund-profiles")
public class SocialFundProfileController {

    private final SocialFundProfileService service;

    public SocialFundProfileController(SocialFundProfileService service) {
        this.service = service;
    }

    @RequirePerm("cmp:social-fund-profile:read")
    @GetMapping
    public R<PageResult<SocialFundProfileResponse>> list(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long size) {
        return R.ok(service.list(page, size));
    }

    @RequirePerm("cmp:social-fund-profile:read")
    @GetMapping("/{id}")
    public R<SocialFundProfileResponse> get(@PathVariable Long id) {
        return R.ok(service.get(id));
    }

    @RequirePerm("cmp:social-fund-profile:write")
    @PostMapping
    public R<SocialFundProfileResponse> create(@Valid @RequestBody SocialFundProfileCreateRequest req) {
        return R.ok(service.create(req));
    }

    @RequirePerm("cmp:social-fund-profile:write")
    @PutMapping("/{id}")
    public R<SocialFundProfileResponse> update(@PathVariable Long id, @Valid @RequestBody SocialFundProfileUpdateRequest req) {
        return R.ok(service.update(id, req));
    }

    @RequirePerm("cmp:social-fund-profile:write")
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return R.ok();
    }
}
