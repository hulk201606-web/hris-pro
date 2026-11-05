package com.hris.recruit.controller;

import com.hris.recruit.dto.RequisitionDtos.RequisitionCreateRequest;
import com.hris.recruit.dto.RequisitionDtos.RequisitionResponse;
import com.hris.recruit.dto.RequisitionDtos.RequisitionUpdateRequest;
import com.hris.recruit.entity.Requisition;
import com.hris.recruit.service.RequisitionService;
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
 * 招聘需求端点：招聘流程起点，权限点 rcu:requisition:read/write。
 *
 * <p>业务域：招聘流程（recruit）；分层：Controller。</p>
 */
@RestController
@RequestMapping("/api/v1/rcu/requisitions")
public class RequisitionController {

    private final RequisitionService service;

    public RequisitionController(RequisitionService service) {
        this.service = service;
    }

    /** 分页查询列表（读权限）。 */
    @RequirePerm("rcu:requisition:read")
    @GetMapping
    public R<PageResult<RequisitionResponse>> list(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long size) {
        return R.ok(service.list(page, size));
    }

    /** 按 ID 查询详情（读权限）。 */
    @RequirePerm("rcu:requisition:read")
    @GetMapping("/{id}")
    public R<RequisitionResponse> get(@PathVariable Long id) {
        return R.ok(service.get(id));
    }

    /** 新建记录（写权限）。 */
    @RequirePerm("rcu:requisition:write")
    @PostMapping
    public R<RequisitionResponse> create(@Valid @RequestBody RequisitionCreateRequest req) {
        return R.ok(service.create(req));
    }

    /** 更新记录（写权限）。 */
    @RequirePerm("rcu:requisition:write")
    @PutMapping("/{id}")
    public R<RequisitionResponse> update(@PathVariable Long id, @Valid @RequestBody RequisitionUpdateRequest req) {
        return R.ok(service.update(id, req));
    }

    /** 提交审批（DRAFT→APPROVING 并发起流程，复用写权限）。 */
    @RequirePerm("rcu:requisition:write")
    @PostMapping("/{id}/submit")
    public R<RequisitionResponse> submit(@PathVariable Long id) {
        return R.ok(service.submit(id));
    }

    /** 删除记录（写权限）。 */
    @RequirePerm("rcu:requisition:write")
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return R.ok();
    }
}
