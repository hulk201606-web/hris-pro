package com.hris.performance.controller;

import com.hris.performance.dto.AppealDtos.AppealCreateRequest;
import com.hris.performance.dto.AppealDtos.AppealResponse;
import com.hris.performance.dto.AppealDtos.AppealUpdateRequest;
import com.hris.performance.entity.Appeal;
import com.hris.performance.service.AppealService;
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
 * Appeal 端点。
 */
@RestController
@RequestMapping("/api/v1/prf/appeals")
public class AppealController {

    private final AppealService service;

    public AppealController(AppealService service) {
        this.service = service;
    }
    /** 分页查询列表。权限点见 @RequirePerm。 */

    @RequirePerm("prf:appeal:read")
    @GetMapping
    public R<PageResult<AppealResponse>> list(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long size) {
        return R.ok(service.list(page, size));
    }
    /** 按 ID 查询详情。权限点见 @RequirePerm。 */

    @RequirePerm("prf:appeal:read")
    @GetMapping("/{id}")
    public R<AppealResponse> get(@PathVariable Long id) {
        return R.ok(service.get(id));
    }
    /** 新建记录。权限点见 @RequirePerm；请求体经 @Valid 校验。 */

    @RequirePerm("prf:appeal:write")
    @PostMapping
    public R<AppealResponse> create(@Valid @RequestBody AppealCreateRequest req) {
        return R.ok(service.create(req));
    }
    /** 按 ID 更新记录。权限点见 @RequirePerm；乐观锁防并发覆盖。 */

    @RequirePerm("prf:appeal:write")
    @PutMapping("/{id}")
    public R<AppealResponse> update(@PathVariable Long id, @Valid @RequestBody AppealUpdateRequest req) {
        return R.ok(service.update(id, req));
    }
    /** 按 ID 删除记录。权限点见 @RequirePerm。 */

    @RequirePerm("prf:appeal:write")
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return R.ok();
    }
}
