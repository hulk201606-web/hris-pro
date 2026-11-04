package com.hris.org.controller;

import com.hris.common.api.R;
import com.hris.common.security.RequirePerm;
import com.hris.org.dto.CatalogRequest;
import com.hris.org.dto.CatalogResponse;
import com.hris.org.service.JobService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 职务端点。
 */
@RestController
@RequestMapping("/api/v1/org")
public class JobController {

    private final JobService jobService;

    public JobController(JobService jobService) {
        this.jobService = jobService;
    }

    /** 查询全部职务（职务字典，如"软件工程师"，与岗位/职级组合刻画职位）。 */
    @RequirePerm("org:job:read")
    @GetMapping("/jobs")
    public R<List<CatalogResponse>> list() {
        return R.ok(jobService.list());
    }

    /** 新建职务：编码缺省自动生成（job 前缀），并校验编码唯一。 */
    @RequirePerm("org:job:write")
    @PostMapping("/jobs")
    public R<CatalogResponse> create(@Valid @RequestBody CatalogRequest req) {
        return R.ok(jobService.create(req));
    }

    /** 更新职务：仅允许改名称与启停状态。 */
    @RequirePerm("org:job:write")
    @PutMapping("/jobs/{id}")
    public R<CatalogResponse> update(@PathVariable Long id, @Valid @RequestBody CatalogRequest req) {
        return R.ok(jobService.update(id, req));
    }

    /** 删除职务（物理删除）。 */
    @RequirePerm("org:job:write")
    @DeleteMapping("/jobs/{id}")
    public R<Void> delete(@PathVariable Long id) {
        jobService.delete(id);
        return R.ok();
    }
}
