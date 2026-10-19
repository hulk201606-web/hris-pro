package com.hris.compensation.controller;

import com.hris.compensation.dto.ImportBatchDtos.ImportBatchCreateRequest;
import com.hris.compensation.dto.ImportBatchDtos.ImportBatchResponse;
import com.hris.compensation.dto.ImportBatchDtos.ImportBatchUpdateRequest;
import com.hris.compensation.entity.ImportBatch;
import com.hris.compensation.service.ImportBatchService;
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
 * ImportBatch 端点。
 */
@RestController
@RequestMapping("/api/v1/cmp/import-batches")
public class ImportBatchController {

    private final ImportBatchService service;

    public ImportBatchController(ImportBatchService service) {
        this.service = service;
    }
    /** 分页查询列表。权限点见 @RequirePerm。 */

    @RequirePerm("cmp:import-batch:read")
    @GetMapping
    public R<PageResult<ImportBatchResponse>> list(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long size) {
        return R.ok(service.list(page, size));
    }
    /** 按 ID 查询详情。权限点见 @RequirePerm。 */

    @RequirePerm("cmp:import-batch:read")
    @GetMapping("/{id}")
    public R<ImportBatchResponse> get(@PathVariable Long id) {
        return R.ok(service.get(id));
    }
    /** 新建记录。权限点见 @RequirePerm；请求体经 @Valid 校验。 */

    @RequirePerm("cmp:import-batch:write")
    @PostMapping
    public R<ImportBatchResponse> create(@Valid @RequestBody ImportBatchCreateRequest req) {
        return R.ok(service.create(req));
    }
    /** 按 ID 更新记录。权限点见 @RequirePerm；乐观锁防并发覆盖。 */

    @RequirePerm("cmp:import-batch:write")
    @PutMapping("/{id}")
    public R<ImportBatchResponse> update(@PathVariable Long id, @Valid @RequestBody ImportBatchUpdateRequest req) {
        return R.ok(service.update(id, req));
    }
    /** 按 ID 删除记录。权限点见 @RequirePerm。 */

    @RequirePerm("cmp:import-batch:write")
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return R.ok();
    }
}
