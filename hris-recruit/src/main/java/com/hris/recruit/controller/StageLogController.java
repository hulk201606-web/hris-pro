package com.hris.recruit.controller;

import com.hris.recruit.dto.StageLogDtos.StageLogCreateRequest;
import com.hris.recruit.dto.StageLogDtos.StageLogResponse;
import com.hris.recruit.dto.StageLogDtos.StageLogUpdateRequest;
import com.hris.recruit.entity.StageLog;
import com.hris.recruit.service.StageLogService;
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
 * 阶段流转日志端点：候选人/投递阶段变更记录查询。
 *
 * <p>业务域：招聘流程（recruit）；分层：Controller。</p>
 */
@RestController
@RequestMapping("/api/v1/rcu/stage-logs")
public class StageLogController {

    private final StageLogService service;

    public StageLogController(StageLogService service) {
        this.service = service;
    }

    /** 分页查询列表（读权限）。 */
    @RequirePerm("rcu:stage-log:read")
    @GetMapping
    public R<PageResult<StageLogResponse>> list(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long size) {
        return R.ok(service.list(page, size));
    }

    /** 按 ID 查询详情（读权限）。 */
    @RequirePerm("rcu:stage-log:read")
    @GetMapping("/{id}")
    public R<StageLogResponse> get(@PathVariable Long id) {
        return R.ok(service.get(id));
    }

    /** 新建记录（写权限）。 */
    @RequirePerm("rcu:stage-log:write")
    @PostMapping
    public R<StageLogResponse> create(@Valid @RequestBody StageLogCreateRequest req) {
        return R.ok(service.create(req));
    }

    /** 更新记录（写权限）。 */
    @RequirePerm("rcu:stage-log:write")
    @PutMapping("/{id}")
    public R<StageLogResponse> update(@PathVariable Long id, @Valid @RequestBody StageLogUpdateRequest req) {
        return R.ok(service.update(id, req));
    }

    /** 删除记录（写权限）。 */
    @RequirePerm("rcu:stage-log:write")
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return R.ok();
    }
}
