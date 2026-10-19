package com.hris.attendance.controller;

import com.hris.attendance.dto.LeaveTypeDtos.LeaveTypeRequest;
import com.hris.attendance.dto.LeaveTypeDtos.LeaveTypeResponse;
import com.hris.attendance.service.LeaveTypeService;
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
 * 假种端点（ATT-F-011）。
 */
@RestController
@RequestMapping("/api/v1/att/leave-types")
public class LeaveTypeController {

    private final LeaveTypeService leaveTypeService;

    public LeaveTypeController(LeaveTypeService leaveTypeService) {
        this.leaveTypeService = leaveTypeService;
    }
    /** 分页查询列表。权限点见 @RequirePerm。 */

    @GetMapping
    public R<List<LeaveTypeResponse>> list() {
        return R.ok(leaveTypeService.list());
    }
    /** 按 ID 查询详情。权限点见 @RequirePerm。 */

    @GetMapping("/{id}")
    public R<LeaveTypeResponse> get(@PathVariable Long id) {
        return R.ok(leaveTypeService.get(id));
    }
    /** 新建记录。权限点见 @RequirePerm；请求体经 @Valid 校验。 */

    @RequirePerm("att:leave:type:write")
    @PostMapping
    public R<LeaveTypeResponse> create(@Valid @RequestBody LeaveTypeRequest req) {
        return R.ok(leaveTypeService.create(req));
    }
    /** 按 ID 更新记录。权限点见 @RequirePerm；乐观锁防并发覆盖。 */

    @RequirePerm("att:leave:type:write")
    @PutMapping("/{id}")
    public R<LeaveTypeResponse> update(@PathVariable Long id,
                                       @Valid @RequestBody LeaveTypeRequest req) {
        return R.ok(leaveTypeService.update(id, req));
    }
}
