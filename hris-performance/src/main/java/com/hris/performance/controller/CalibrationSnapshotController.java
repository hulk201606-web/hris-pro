package com.hris.performance.controller;

import com.hris.performance.dto.CalibrationSnapshotDtos.CalibrationSnapshotCreateRequest;
import com.hris.performance.dto.CalibrationSnapshotDtos.CalibrationSnapshotResponse;
import com.hris.performance.dto.CalibrationSnapshotDtos.CalibrationSnapshotUpdateRequest;
import com.hris.performance.entity.CalibrationSnapshot;
import com.hris.performance.service.CalibrationSnapshotService;
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
 * CalibrationSnapshot 端点。
 */
@RestController
@RequestMapping("/api/v1/prf/calibration-snapshots")
public class CalibrationSnapshotController {

    private final CalibrationSnapshotService service;

    public CalibrationSnapshotController(CalibrationSnapshotService service) {
        this.service = service;
    }
    /** 分页查询列表。权限点见 @RequirePerm。 */

    @RequirePerm("prf:calibration-snapshot:read")
    @GetMapping
    public R<PageResult<CalibrationSnapshotResponse>> list(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long size) {
        return R.ok(service.list(page, size));
    }
    /** 按 ID 查询详情。权限点见 @RequirePerm。 */

    @RequirePerm("prf:calibration-snapshot:read")
    @GetMapping("/{id}")
    public R<CalibrationSnapshotResponse> get(@PathVariable Long id) {
        return R.ok(service.get(id));
    }
    /** 新建记录。权限点见 @RequirePerm；请求体经 @Valid 校验。 */

    @RequirePerm("prf:calibration-snapshot:write")
    @PostMapping
    public R<CalibrationSnapshotResponse> create(@Valid @RequestBody CalibrationSnapshotCreateRequest req) {
        return R.ok(service.create(req));
    }
    /** 按 ID 更新记录。权限点见 @RequirePerm；乐观锁防并发覆盖。 */

    @RequirePerm("prf:calibration-snapshot:write")
    @PutMapping("/{id}")
    public R<CalibrationSnapshotResponse> update(@PathVariable Long id, @Valid @RequestBody CalibrationSnapshotUpdateRequest req) {
        return R.ok(service.update(id, req));
    }
    /** 按 ID 删除记录。权限点见 @RequirePerm。 */

    @RequirePerm("prf:calibration-snapshot:write")
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return R.ok();
    }
}
