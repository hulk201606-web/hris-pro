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

    /** 部门列表/树查询：tree=true 返回嵌套部门树，否则返回扁平的当前生效部门列表（按物化路径排序）。 */
    @RequirePerm("org:dept:read")
    @GetMapping("/depts")
    public R<List<DeptNode>> list(@RequestParam(defaultValue = "false") boolean tree) {
        return R.ok(deptService.query(tree));
    }

    /** 新建部门：自动生成物化路径，校验树深度（≤8 层）与部门编码唯一；父部门为空则建根节点。 */
    @RequirePerm("org:dept:write")
    @PostMapping("/depts")
    public R<DeptNode> create(@Valid @RequestBody DeptRequest req) {
        return R.ok(deptService.create(req));
    }

    /** 更新部门：变更前先写 org_dept_history 快照；父部门变更时重算物化路径，部门编码不可修改。 */
    @RequirePerm("org:dept:write")
    @PutMapping("/depts/{id}")
    public R<DeptNode> update(@PathVariable Long id, @Valid @RequestBody DeptRequest req) {
        return R.ok(deptService.update(id, req));
    }

    /** 部门历史快照：at 为空取当日；优先取历史表，无快照时用现行部门现拼一份。 */
    @RequirePerm("org:dept:read")
    @GetMapping("/depts/{id}/snapshot")
    public R<Map<String, Object>> snapshot(
            @PathVariable Long id,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate at) {
        return R.ok(deptService.snapshot(id, at));
    }

    /**
     * 发起组织调整申请（权限点 org:dept:adjust）：落历史快照并驱动流程引擎（ORG_ADJUST），
     * 返回业务键 bizKey 与流程实例 ID；MVP 下流程调用降级不阻断落单。
     */
    @RequirePerm("org:dept:adjust")
    @PostMapping("/depts/adjust-requests")
    public R<AdjustRequestResponse> adjust(@Valid @RequestBody AdjustRequestCreate req) {
        return R.ok(deptService.adjust(req));
    }
}
