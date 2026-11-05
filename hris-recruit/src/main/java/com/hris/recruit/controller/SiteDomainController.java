package com.hris.recruit.controller;

import com.hris.common.api.R;
import com.hris.common.dto.PageResult;
import com.hris.common.security.RequirePerm;
import com.hris.recruit.dto.SiteDomainDtos.SiteDomainCreateRequest;
import com.hris.recruit.dto.SiteDomainDtos.SiteDomainResponse;
import com.hris.recruit.dto.SiteDomainDtos.SiteDomainUpdateRequest;
import com.hris.recruit.service.SiteDomainService;
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
 * 官网租户域名映射端点（批 7 #13）：管理端 CRUD，权限点 rcu:site:read/write。
 *
 * <p>业务域：招聘流程（recruit）；分层：Controller。租户隔离由租户插件兜底，
 * domain 全局唯一（uk）。新增权限点需执行 scripts/seed_rbac_from_source.py 同步到角色。</p>
 */
@RestController
@RequestMapping("/api/v1/rcu/site-domains")
public class SiteDomainController {

    private final SiteDomainService service;

    public SiteDomainController(SiteDomainService service) {
        this.service = service;
    }

    /** 分页查询列表（读权限，租户隔离）。 */
    @RequirePerm("rcu:site:read")
    @GetMapping
    public R<PageResult<SiteDomainResponse>> list(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long size) {
        return R.ok(service.list(page, size));
    }

    /** 新建映射（写权限）：domain 全局唯一，冲突转 PARAM_INVALID。 */
    @RequirePerm("rcu:site:write")
    @PostMapping
    public R<SiteDomainResponse> create(@Valid @RequestBody SiteDomainCreateRequest req) {
        return R.ok(service.create(req));
    }

    /** 更新映射（写权限）：可改域名与状态。 */
    @RequirePerm("rcu:site:write")
    @PutMapping("/{id}")
    public R<SiteDomainResponse> update(@PathVariable Long id,
                                        @Valid @RequestBody SiteDomainUpdateRequest req) {
        return R.ok(service.update(id, req));
    }

    /** 删除映射（写权限，逻辑删除）。 */
    @RequirePerm("rcu:site:write")
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return R.ok();
    }
}
