package com.hris.attendance.controller;

import com.hris.attendance.dto.GroupDtos.GroupRequest;
import com.hris.attendance.dto.GroupDtos.GroupResponse;
import com.hris.attendance.service.GroupService;
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
 * 考勤班组端点（ATT-F-001）。
 */
@RestController
@RequestMapping("/api/v1/att/groups")
public class GroupController {

    private final GroupService groupService;

    public GroupController(GroupService groupService) {
        this.groupService = groupService;
    }

    @GetMapping
    public R<List<GroupResponse>> list() {
        return R.ok(groupService.list());
    }

    @GetMapping("/{id}")
    public R<GroupResponse> get(@PathVariable Long id) {
        return R.ok(groupService.get(id));
    }

    @RequirePerm("att:group:write")
    @PostMapping
    public R<GroupResponse> create(@Valid @RequestBody GroupRequest req) {
        return R.ok(groupService.create(req));
    }

    @RequirePerm("att:group:write")
    @PutMapping("/{id}")
    public R<GroupResponse> update(@PathVariable Long id, @Valid @RequestBody GroupRequest req) {
        return R.ok(groupService.update(id, req));
    }
}
