package com.hris.compensation.controller;

import com.hris.compensation.dto.TaxDeductionDtos.TaxDeductionCreateRequest;
import com.hris.compensation.dto.TaxDeductionDtos.TaxDeductionResponse;
import com.hris.compensation.dto.TaxDeductionDtos.TaxDeductionUpdateRequest;
import com.hris.compensation.entity.TaxDeduction;
import com.hris.compensation.service.TaxDeductionService;
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
 * TaxDeduction 端点。
 */
@RestController
@RequestMapping("/api/v1/cmp/tax-deductions")
public class TaxDeductionController {

    private final TaxDeductionService service;

    public TaxDeductionController(TaxDeductionService service) {
        this.service = service;
    }
    /** 分页查询列表。权限点见 @RequirePerm。 */

    @RequirePerm("cmp:tax-deduction:read")
    @GetMapping
    public R<PageResult<TaxDeductionResponse>> list(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long size) {
        return R.ok(service.list(page, size));
    }
    /** 按 ID 查询详情。权限点见 @RequirePerm。 */

    @RequirePerm("cmp:tax-deduction:read")
    @GetMapping("/{id}")
    public R<TaxDeductionResponse> get(@PathVariable Long id) {
        return R.ok(service.get(id));
    }
    /** 新建记录。权限点见 @RequirePerm；请求体经 @Valid 校验。 */

    @RequirePerm("cmp:tax-deduction:write")
    @PostMapping
    public R<TaxDeductionResponse> create(@Valid @RequestBody TaxDeductionCreateRequest req) {
        return R.ok(service.create(req));
    }
    /** 按 ID 更新记录。权限点见 @RequirePerm；乐观锁防并发覆盖。 */

    @RequirePerm("cmp:tax-deduction:write")
    @PutMapping("/{id}")
    public R<TaxDeductionResponse> update(@PathVariable Long id, @Valid @RequestBody TaxDeductionUpdateRequest req) {
        return R.ok(service.update(id, req));
    }
    /** 按 ID 删除记录。权限点见 @RequirePerm。 */

    @RequirePerm("cmp:tax-deduction:write")
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return R.ok();
    }
}
