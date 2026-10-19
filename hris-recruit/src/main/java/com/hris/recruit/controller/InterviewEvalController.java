package com.hris.recruit.controller;

import com.hris.recruit.dto.InterviewEvalDtos.InterviewEvalCreateRequest;
import com.hris.recruit.dto.InterviewEvalDtos.InterviewEvalResponse;
import com.hris.recruit.dto.InterviewEvalDtos.InterviewEvalUpdateRequest;
import com.hris.recruit.entity.InterviewEval;
import com.hris.recruit.service.InterviewEvalService;
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
 * 面试评价端点：面试官打分/评语 CRUD。
 *
 * <p>业务域：招聘流程（recruit）；分层：Controller。</p>
 */
@RestController
@RequestMapping("/api/v1/rcu/interview-evals")
public class InterviewEvalController {

    private final InterviewEvalService service;

    public InterviewEvalController(InterviewEvalService service) {
        this.service = service;
    }

    /** 分页查询列表（读权限）。 */
    @RequirePerm("rcu:interview-eval:read")
    @GetMapping
    public R<PageResult<InterviewEvalResponse>> list(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long size) {
        return R.ok(service.list(page, size));
    }

    /** 按 ID 查询详情（读权限）。 */
    @RequirePerm("rcu:interview-eval:read")
    @GetMapping("/{id}")
    public R<InterviewEvalResponse> get(@PathVariable Long id) {
        return R.ok(service.get(id));
    }

    /** 新建记录（写权限）。 */
    @RequirePerm("rcu:interview-eval:write")
    @PostMapping
    public R<InterviewEvalResponse> create(@Valid @RequestBody InterviewEvalCreateRequest req) {
        return R.ok(service.create(req));
    }

    /** 更新记录（写权限）。 */
    @RequirePerm("rcu:interview-eval:write")
    @PutMapping("/{id}")
    public R<InterviewEvalResponse> update(@PathVariable Long id, @Valid @RequestBody InterviewEvalUpdateRequest req) {
        return R.ok(service.update(id, req));
    }

    /** 删除记录（写权限）。 */
    @RequirePerm("rcu:interview-eval:write")
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return R.ok();
    }
}
