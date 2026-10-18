package com.hris.org.controller;

import com.hris.common.api.R;
import com.hris.common.security.RequirePerm;
import com.hris.org.dto.AdjustRequestCreate;
import com.hris.org.dto.AdjustRequestResponse;
import com.hris.org.dto.DeptNode;
import com.hris.org.dto.DeptRequest;
import com.hris.org.service.DeptService;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 部门端点（树查询/创建/更新/历史快照/组织调整申请）。
 */
@RestController
@RequestMapping("/api/v1/org")
public class DeptController {

    private final DeptService deptService;

    public DeptController(DeptService deptService) {
        this.deptService = deptService;
    }

    @GetMapping("/depts")
    public R<List<DeptNode>> list(@RequestParam(defaultValue = "false") boolean tree) {
        return R.ok(deptService.query(tree));
    }

    @PostMapping("/depts")
    public R<DeptNode> create(@Valid @RequestBody DeptRequest req) {
        return R.ok(deptService.create(req));
    }

    @PutMapping("/depts/{id}")
    public R<DeptNode> update(@PathVariable Long id, @Valid @RequestBody DeptRequest req) {
        return R.ok(deptService.update(id, req));
    }

    @GetMapping("/depts/{id}/snapshot")
    public R<Map<String, Object>> snapshot(
            @PathVariable Long id,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate at) {
        return R.ok(deptService.snapshot(id, at));
    }

    @RequirePerm("org:dept:adjust")
    @PostMapping("/depts/adjust-requests")
    public R<AdjustRequestResponse> adjust(@Valid @RequestBody AdjustRequestCreate req) {
        return R.ok(deptService.adjust(req));
    }
}
