package com.hris.recruit.controller;

import com.hris.recruit.dto.CandidateDtos.CandidateCreateRequest;
import com.hris.recruit.dto.CandidateDtos.CandidateResponse;
import com.hris.recruit.dto.CandidateDtos.CandidateUpdateRequest;
import com.hris.recruit.entity.Candidate;
import com.hris.recruit.service.CandidateService;
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
 * 候选人主档端点：人才库 CRUD，权限点 rcu:candidate:read/write。
 *
 * <p>业务域：招聘流程（recruit）；分层：Controller。</p>
 */
@RestController
@RequestMapping("/api/v1/rcu/candidates")
public class CandidateController {

    private final CandidateService service;

    public CandidateController(CandidateService service) {
        this.service = service;
    }

    /** 分页查询列表（读权限）。 */
    @RequirePerm("rcu:candidate:read")
    @GetMapping
    public R<PageResult<CandidateResponse>> list(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long size) {
        return R.ok(service.list(page, size));
    }

    /** 按 ID 查询详情（读权限）。 */
    @RequirePerm("rcu:candidate:read")
    @GetMapping("/{id}")
    public R<CandidateResponse> get(@PathVariable Long id) {
        return R.ok(service.get(id));
    }

    /** 新建记录（写权限）。 */
    @RequirePerm("rcu:candidate:write")
    @PostMapping
    public R<CandidateResponse> create(@Valid @RequestBody CandidateCreateRequest req) {
        return R.ok(service.create(req));
    }

    /** 更新记录（写权限）。 */
    @RequirePerm("rcu:candidate:write")
    @PutMapping("/{id}")
    public R<CandidateResponse> update(@PathVariable Long id, @Valid @RequestBody CandidateUpdateRequest req) {
        return R.ok(service.update(id, req));
    }

    /** 删除记录（写权限）。 */
    @RequirePerm("rcu:candidate:write")
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return R.ok();
    }
}
