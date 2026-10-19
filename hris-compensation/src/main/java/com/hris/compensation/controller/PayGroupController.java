package com.hris.compensation.controller;

import com.hris.compensation.dto.PayGroupDtos.PayGroupCreateRequest;
import com.hris.compensation.dto.PayGroupDtos.PayGroupResponse;
import com.hris.compensation.dto.PayGroupDtos.PayGroupUpdateRequest;
import com.hris.compensation.entity.PayGroup;
import com.hris.compensation.service.PayGroupService;
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
 * PayGroup 端点。
 */
@RestController
@RequestMapping("/api/v1/cmp/pay-groups")
public class PayGroupController {

    private final PayGroupService service;

    public PayGroupController(PayGroupService service) {
        this.service = service;
    }
    /** 分页查询列表。权限点见 @RequirePerm。 */

    @RequirePerm("cmp:pay-group:read")
    @GetMapping
    public R<PageResult<PayGroupResponse>> list(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long size) {
        return R.ok(service.list(page, size));
    }
    /** 按 ID 查询详情。权限点见 @RequirePerm。 */

    @RequirePerm("cmp:pay-group:read")
    @GetMapping("/{id}")
    public R<PayGroupResponse> get(@PathVariable Long id) {
        return R.ok(service.get(id));
    }
    /** 新建记录。权限点见 @RequirePerm；请求体经 @Valid 校验。 */

    @RequirePerm("cmp:pay-group:write")
    @PostMapping
    public R<PayGroupResponse> create(@Valid @RequestBody PayGroupCreateRequest req) {
        return R.ok(service.create(req));
    }
    /** 按 ID 更新记录。权限点见 @RequirePerm；乐观锁防并发覆盖。 */

    @RequirePerm("cmp:pay-group:write")
    @PutMapping("/{id}")
    public R<PayGroupResponse> update(@PathVariable Long id, @Valid @RequestBody PayGroupUpdateRequest req) {
        return R.ok(service.update(id, req));
    }
    /** 按 ID 删除记录。权限点见 @RequirePerm。 */

    @RequirePerm("cmp:pay-group:write")
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return R.ok();
    }
}
