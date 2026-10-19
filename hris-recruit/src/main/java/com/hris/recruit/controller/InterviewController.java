package com.hris.recruit.controller;

import com.hris.recruit.dto.InterviewDtos.InterviewCreateRequest;
import com.hris.recruit.dto.InterviewDtos.InterviewResponse;
import com.hris.recruit.dto.InterviewDtos.InterviewUpdateRequest;
import com.hris.recruit.entity.Interview;
import com.hris.recruit.service.InterviewService;
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
 * 面试安排端点：为投递记录安排各轮面试，权限点 rcu:interview:*。
 *
 * <p>业务域：招聘流程（recruit）；分层：Controller。</p>
 */
@RestController
@RequestMapping("/api/v1/rcu/interviews")
public class InterviewController {

    private final InterviewService service;

    public InterviewController(InterviewService service) {
        this.service = service;
    }

    /** 分页查询列表（读权限）。 */
    @RequirePerm("rcu:interview:read")
    @GetMapping
    public R<PageResult<InterviewResponse>> list(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long size) {
        return R.ok(service.list(page, size));
    }

    /** 按 ID 查询详情（读权限）。 */
    @RequirePerm("rcu:interview:read")
    @GetMapping("/{id}")
    public R<InterviewResponse> get(@PathVariable Long id) {
        return R.ok(service.get(id));
    }

    /** 新建记录（写权限）。 */
    @RequirePerm("rcu:interview:write")
    @PostMapping
    public R<InterviewResponse> create(@Valid @RequestBody InterviewCreateRequest req) {
        return R.ok(service.create(req));
    }

    /** 更新记录（写权限）。 */
    @RequirePerm("rcu:interview:write")
    @PutMapping("/{id}")
    public R<InterviewResponse> update(@PathVariable Long id, @Valid @RequestBody InterviewUpdateRequest req) {
        return R.ok(service.update(id, req));
    }

    /** 删除记录（写权限）。 */
    @RequirePerm("rcu:interview:write")
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return R.ok();
    }
}
