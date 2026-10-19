package com.hris.compensation.controller;

import com.hris.compensation.dto.PayslipDtos.PayslipCreateRequest;
import com.hris.compensation.dto.PayslipDtos.PayslipResponse;
import com.hris.compensation.dto.PayslipDtos.PayslipUpdateRequest;
import com.hris.compensation.entity.Payslip;
import com.hris.compensation.service.PayslipService;
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
 * Payslip 端点。
 */
@RestController
@RequestMapping("/api/v1/cmp/payslips")
public class PayslipController {

    private final PayslipService service;

    public PayslipController(PayslipService service) {
        this.service = service;
    }
    /** 分页查询列表。权限点见 @RequirePerm。 */

    @RequirePerm("cmp:payslip:read")
    @GetMapping
    public R<PageResult<PayslipResponse>> list(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long size) {
        return R.ok(service.list(page, size));
    }
    /** 按 ID 查询详情。权限点见 @RequirePerm。 */

    @RequirePerm("cmp:payslip:read")
    @GetMapping("/{id}")
    public R<PayslipResponse> get(@PathVariable Long id) {
        return R.ok(service.get(id));
    }
    /** 新建记录。权限点见 @RequirePerm；请求体经 @Valid 校验。 */

    @RequirePerm("cmp:payslip:write")
    @PostMapping
    public R<PayslipResponse> create(@Valid @RequestBody PayslipCreateRequest req) {
        return R.ok(service.create(req));
    }
    /** 按 ID 更新记录。权限点见 @RequirePerm；乐观锁防并发覆盖。 */

    @RequirePerm("cmp:payslip:write")
    @PutMapping("/{id}")
    public R<PayslipResponse> update(@PathVariable Long id, @Valid @RequestBody PayslipUpdateRequest req) {
        return R.ok(service.update(id, req));
    }
    /** 按 ID 删除记录。权限点见 @RequirePerm。 */

    @RequirePerm("cmp:payslip:write")
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return R.ok();
    }
}
