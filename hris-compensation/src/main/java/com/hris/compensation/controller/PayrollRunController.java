package com.hris.compensation.controller;

import com.hris.compensation.dto.PayrollRunDtos.PayrollRunCreateRequest;
import com.hris.compensation.dto.PayrollRunDtos.PayrollRunResponse;
import com.hris.compensation.dto.PayrollRunDtos.PayrollRunUpdateRequest;
import com.hris.compensation.entity.PayrollRun;
import com.hris.compensation.service.PayrollRunService;
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
 * PayrollRun 端点。
 */
@RestController
@RequestMapping("/api/v1/cmp/payroll-runs")
public class PayrollRunController {

    private final PayrollRunService service;

    public PayrollRunController(PayrollRunService service) {
        this.service = service;
    }
    /** 分页查询列表。权限点见 @RequirePerm。 */

    @RequirePerm("cmp:payroll-run:read")
    @GetMapping
    public R<PageResult<PayrollRunResponse>> list(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long size) {
        return R.ok(service.list(page, size));
    }
    /** 按 ID 查询详情。权限点见 @RequirePerm。 */

    @RequirePerm("cmp:payroll-run:read")
    @GetMapping("/{id}")
    public R<PayrollRunResponse> get(@PathVariable Long id) {
        return R.ok(service.get(id));
    }
    /** 新建记录。权限点见 @RequirePerm；请求体经 @Valid 校验。 */

    @RequirePerm("cmp:payroll-run:write")
    @PostMapping
    public R<PayrollRunResponse> create(@Valid @RequestBody PayrollRunCreateRequest req) {
        return R.ok(service.create(req));
    }
    /** 按 ID 更新记录。权限点见 @RequirePerm；乐观锁防并发覆盖。 */

    @RequirePerm("cmp:payroll-run:write")
    @PutMapping("/{id}")
    public R<PayrollRunResponse> update(@PathVariable Long id, @Valid @RequestBody PayrollRunUpdateRequest req) {
        return R.ok(service.update(id, req));
    }
    /** 提交审批（INIT/TRIAL/CALC→REVIEW 并发起流程，复用写权限）。 */

    @RequirePerm("cmp:payroll-run:write")
    @PostMapping("/{id}/submit")
    public R<PayrollRunResponse> submit(@PathVariable Long id) {
        return R.ok(service.submit(id));
    }
    /** 按 ID 删除记录。权限点见 @RequirePerm。 */

    @RequirePerm("cmp:payroll-run:write")
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return R.ok();
    }
}
