package com.hris.recruit.controller;

import com.hris.recruit.dto.TalentPoolTagDtos.TalentPoolTagCreateRequest;
import com.hris.recruit.dto.TalentPoolTagDtos.TalentPoolTagResponse;
import com.hris.recruit.dto.TalentPoolTagDtos.TalentPoolTagUpdateRequest;
import com.hris.recruit.entity.TalentPoolTag;
import com.hris.recruit.service.TalentPoolTagService;
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
 * 人才库标签端点：候选人标签 CRUD。
 *
 * <p>业务域：招聘流程（recruit）；分层：Controller。</p>
 */
@RestController
@RequestMapping("/api/v1/rcu/talent-pool-tags")
public class TalentPoolTagController {

    private final TalentPoolTagService service;

    public TalentPoolTagController(TalentPoolTagService service) {
        this.service = service;
    }

    /** 分页查询列表（读权限）。 */
    @RequirePerm("rcu:talent-pool-tag:read")
    @GetMapping
    public R<PageResult<TalentPoolTagResponse>> list(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long size) {
        return R.ok(service.list(page, size));
    }

    /** 按 ID 查询详情（读权限）。 */
    @RequirePerm("rcu:talent-pool-tag:read")
    @GetMapping("/{id}")
    public R<TalentPoolTagResponse> get(@PathVariable Long id) {
        return R.ok(service.get(id));
    }

    /** 新建记录（写权限）。 */
    @RequirePerm("rcu:talent-pool-tag:write")
    @PostMapping
    public R<TalentPoolTagResponse> create(@Valid @RequestBody TalentPoolTagCreateRequest req) {
        return R.ok(service.create(req));
    }

    /** 更新记录（写权限）。 */
    @RequirePerm("rcu:talent-pool-tag:write")
    @PutMapping("/{id}")
    public R<TalentPoolTagResponse> update(@PathVariable Long id, @Valid @RequestBody TalentPoolTagUpdateRequest req) {
        return R.ok(service.update(id, req));
    }

    /** 删除记录（写权限）。 */
    @RequirePerm("rcu:talent-pool-tag:write")
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return R.ok();
    }
}
