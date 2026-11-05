package com.hris.recruit.controller;

import com.hris.recruit.dto.ResumeDtos.ResumeCleanupResponse;
import com.hris.recruit.dto.ResumeDtos.ResumeCreateRequest;
import com.hris.recruit.dto.ResumeDtos.ResumeResponse;
import com.hris.recruit.dto.ResumeDtos.ResumeUpdateRequest;
import com.hris.recruit.entity.Resume;
import com.hris.recruit.service.ResumeService;
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
 * 简历端点：候选人简历附件与解析结果维护。
 *
 * <p>业务域：招聘流程（recruit）；分层：Controller。</p>
 */
@RestController
@RequestMapping("/api/v1/rcu/resumes")
public class ResumeController {

    private final ResumeService service;

    public ResumeController(ResumeService service) {
        this.service = service;
    }

    /** 分页查询列表（读权限）。 */
    @RequirePerm("rcu:resume:read")
    @GetMapping
    public R<PageResult<ResumeResponse>> list(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long size) {
        return R.ok(service.list(page, size));
    }

    /** 按 ID 查询详情（读权限）。 */
    @RequirePerm("rcu:resume:read")
    @GetMapping("/{id}")
    public R<ResumeResponse> get(@PathVariable Long id) {
        return R.ok(service.get(id));
    }

    /** 新建记录（写权限）。 */
    @RequirePerm("rcu:resume:write")
    @PostMapping
    public R<ResumeResponse> create(@Valid @RequestBody ResumeCreateRequest req) {
        return R.ok(service.create(req));
    }

    /** 更新记录（写权限）。 */
    @RequirePerm("rcu:resume:write")
    @PutMapping("/{id}")
    public R<ResumeResponse> update(@PathVariable Long id, @Valid @RequestBody ResumeUpdateRequest req) {
        return R.ok(service.update(id, req));
    }

    /** 删除记录（写权限）。 */
    @RequirePerm("rcu:resume:write")
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return R.ok();
    }

    /**
     * 清理过期简历（RCS-F-022，写权限）：逻辑删除 retention_until < today 的记录并清空引用。
     * 由运营在 platform 调度中心注册为日任务，本模块不内置 @Scheduled。
     */
    @RequirePerm("rcu:resume:write")
    @PostMapping("/cleanup")
    public R<ResumeCleanupResponse> cleanup() {
        return R.ok(new ResumeCleanupResponse(service.cleanupExpired()));
    }
}
