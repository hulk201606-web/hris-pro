package com.hris.performance.controller;

import com.hris.performance.dto.ForcedRuleDtos.ForcedRuleCreateRequest;
import com.hris.performance.dto.ForcedRuleDtos.ForcedRuleResponse;
import com.hris.performance.dto.ForcedRuleDtos.ForcedRuleUpdateRequest;
import com.hris.performance.entity.ForcedRule;
import com.hris.performance.service.ForcedRuleService;
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
 * ForcedRule 端点。
 */
@RestController
@RequestMapping("/api/v1/prf/forced-rules")
public class ForcedRuleController {

    private final ForcedRuleService service;

    public ForcedRuleController(ForcedRuleService service) {
        this.service = service;
    }
    /** 分页查询列表。权限点见 @RequirePerm。 */

    @RequirePerm("prf:forced-rule:read")
    @GetMapping
    public R<PageResult<ForcedRuleResponse>> list(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long size) {
        return R.ok(service.list(page, size));
    }
    /** 按 ID 查询详情。权限点见 @RequirePerm。 */

    @RequirePerm("prf:forced-rule:read")
    @GetMapping("/{id}")
    public R<ForcedRuleResponse> get(@PathVariable Long id) {
        return R.ok(service.get(id));
    }
    /** 新建记录。权限点见 @RequirePerm；请求体经 @Valid 校验。 */

    @RequirePerm("prf:forced-rule:write")
    @PostMapping
    public R<ForcedRuleResponse> create(@Valid @RequestBody ForcedRuleCreateRequest req) {
        return R.ok(service.create(req));
    }
    /** 按 ID 更新记录。权限点见 @RequirePerm；乐观锁防并发覆盖。 */

    @RequirePerm("prf:forced-rule:write")
    @PutMapping("/{id}")
    public R<ForcedRuleResponse> update(@PathVariable Long id, @Valid @RequestBody ForcedRuleUpdateRequest req) {
        return R.ok(service.update(id, req));
    }
    /** 按 ID 删除记录。权限点见 @RequirePerm。 */

    @RequirePerm("prf:forced-rule:write")
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return R.ok();
    }
}
