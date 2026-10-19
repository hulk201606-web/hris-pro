package com.hris.attendance.controller;

import com.hris.attendance.dto.ShiftDtos.ShiftRequest;
import com.hris.attendance.dto.ShiftDtos.ShiftResponse;
import com.hris.attendance.service.ShiftService;
import com.hris.common.api.R;
import com.hris.common.security.RequirePerm;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 班次端点（ATT-F-002）。
 */
@RestController
@RequestMapping("/api/v1/att/shifts")
public class ShiftController {

    private final ShiftService shiftService;

    public ShiftController(ShiftService shiftService) {
        this.shiftService = shiftService;
    }
    /** 分页查询列表。权限点见 @RequirePerm。 */

    @GetMapping
    public R<List<ShiftResponse>> list() {
        return R.ok(shiftService.list());
    }
    /** 按 ID 查询详情。权限点见 @RequirePerm。 */

    @GetMapping("/{id}")
    public R<ShiftResponse> get(@PathVariable Long id) {
        return R.ok(shiftService.get(id));
    }
    /** 新建记录。权限点见 @RequirePerm；请求体经 @Valid 校验。 */

    @RequirePerm("att:shift:write")
    @PostMapping
    public R<ShiftResponse> create(@Valid @RequestBody ShiftRequest req) {
        return R.ok(shiftService.create(req));
    }
    /** 按 ID 更新记录。权限点见 @RequirePerm；乐观锁防并发覆盖。 */

    @RequirePerm("att:shift:write")
    @PutMapping("/{id}")
    public R<ShiftResponse> update(@PathVariable Long id, @Valid @RequestBody ShiftRequest req) {
        return R.ok(shiftService.update(id, req));
    }
}
