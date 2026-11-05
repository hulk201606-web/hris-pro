package com.hris.performance.controller;

import com.hris.performance.dto.CycleDtos.CycleCreateRequest;
import com.hris.performance.dto.CycleDtos.CycleResponse;
import com.hris.performance.dto.CycleDtos.CycleTransitionRequest;
import com.hris.performance.dto.CycleDtos.CycleUpdateRequest;
import com.hris.performance.entity.Cycle;
import com.hris.performance.service.CycleService;
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
 * Cycle 端点。
 */
@RestController
@RequestMapping("/api/v1/prf/cycles")
public class CycleController {

    private final CycleService service;

    public CycleController(CycleService service) {
        this.service = service;
    }
    /** 分页查询列表。权限点见 @RequirePerm。 */

    @RequirePerm("prf:cycle:read")
    @GetMapping
    public R<PageResult<CycleResponse>> list(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long size) {
        return R.ok(service.list(page, size));
    }
    /** 按 ID 查询详情。权限点见 @RequirePerm。 */

    @RequirePerm("prf:cycle:read")
    @GetMapping("/{id}")
    public R<CycleResponse> get(@PathVariable Long id) {
        return R.ok(service.get(id));
    }
    /** 新建记录。权限点见 @RequirePerm；请求体经 @Valid 校验。 */

    @RequirePerm("prf:cycle:write")
    @PostMapping
    public R<CycleResponse> create(@Valid @RequestBody CycleCreateRequest req) {
        return R.ok(service.create(req));
    }
    /** 按 ID 更新记录。权限点见 @RequirePerm；乐观锁防并发覆盖。 */

    @RequirePerm("prf:cycle:write")
    @PutMapping("/{id}")
    public R<CycleResponse> update(@PathVariable Long id, @Valid @RequestBody CycleUpdateRequest req) {
        return R.ok(service.update(id, req));
    }
    /** 按 ID 删除记录。权限点见 @RequirePerm。 */

    @RequirePerm("prf:cycle:write")
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return R.ok();
    }

    /**
     * 周期状态流转（PRF-F-002）：DRAFT→ACTIVE→SCORING→CLOSED 白名单校验，
     * 非法流转返回 PARAM_INVALID(10001)。
     */
    @RequirePerm("prf:cycle:write")
    @PostMapping("/{id}/transition")
    public R<CycleResponse> transition(@PathVariable Long id,
                                       @Valid @RequestBody CycleTransitionRequest req) {
        return R.ok(service.transition(id, req.target()));
    }
}
