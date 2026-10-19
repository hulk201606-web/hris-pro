package com.hris.performance.controller;

import com.hris.performance.dto.ClassSessionDtos.ClassSessionCreateRequest;
import com.hris.performance.dto.ClassSessionDtos.ClassSessionResponse;
import com.hris.performance.dto.ClassSessionDtos.ClassSessionUpdateRequest;
import com.hris.performance.entity.ClassSession;
import com.hris.performance.service.ClassSessionService;
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
 * ClassSession 端点。
 */
@RestController
@RequestMapping("/api/v1/prf/class-sessions")
public class ClassSessionController {

    private final ClassSessionService service;

    public ClassSessionController(ClassSessionService service) {
        this.service = service;
    }
    /** 分页查询列表。权限点见 @RequirePerm。 */

    @RequirePerm("prf:class-session:read")
    @GetMapping
    public R<PageResult<ClassSessionResponse>> list(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long size) {
        return R.ok(service.list(page, size));
    }
    /** 按 ID 查询详情。权限点见 @RequirePerm。 */

    @RequirePerm("prf:class-session:read")
    @GetMapping("/{id}")
    public R<ClassSessionResponse> get(@PathVariable Long id) {
        return R.ok(service.get(id));
    }
    /** 新建记录。权限点见 @RequirePerm；请求体经 @Valid 校验。 */

    @RequirePerm("prf:class-session:write")
    @PostMapping
    public R<ClassSessionResponse> create(@Valid @RequestBody ClassSessionCreateRequest req) {
        return R.ok(service.create(req));
    }
    /** 按 ID 更新记录。权限点见 @RequirePerm；乐观锁防并发覆盖。 */

    @RequirePerm("prf:class-session:write")
    @PutMapping("/{id}")
    public R<ClassSessionResponse> update(@PathVariable Long id, @Valid @RequestBody ClassSessionUpdateRequest req) {
        return R.ok(service.update(id, req));
    }
    /** 按 ID 删除记录。权限点见 @RequirePerm。 */

    @RequirePerm("prf:class-session:write")
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return R.ok();
    }
}
