package com.hris.org.controller;

import com.hris.common.api.R;
import com.hris.common.security.RequirePerm;
import com.hris.org.dto.HeadcountRequest;
import com.hris.org.dto.HeadcountUsage;
import com.hris.org.service.HeadcountService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 编制端点（查询/设定/看板）。
 */
@RestController
@RequestMapping("/api/v1/org")
public class HeadcountController {

    private final HeadcountService headcountService;

    public HeadcountController(HeadcountService headcountService) {
        this.headcountService = headcountService;
    }

    /** 编制查询：可按部门/岗位/季度（如 2026Q3）过滤，返回编制额度、已占用、空缺、占用率。 */
    @RequirePerm("org:headcount:read")
    @GetMapping("/headcounts")
    public R<List<HeadcountUsage>> list(@RequestParam(required = false) Long deptId,
                                        @RequestParam(required = false) Long positionId,
                                        @RequestParam(required = false) String period) {
        return R.ok(headcountService.query(deptId, positionId, period));
    }

    /**
     * 设定编制（权限点 org:headcount:write）：同一部门+岗位+季度唯一，存在则覆盖额度，
     * 首次落库已占用 occupied=0（后续由员工入职流程回写）。
     */
    @RequirePerm("org:headcount:write")
    @PutMapping("/headcounts")
    public R<HeadcountUsage> set(@Valid @RequestBody HeadcountRequest req) {
        return R.ok(headcountService.set(req));
    }

    /** 编制占用看板：按部门/季度汇总（不带岗位维度），用于编制缺口分析。 */
    @RequirePerm("org:headcount:read")
    @GetMapping("/headcounts/usage")
    public R<List<HeadcountUsage>> usage(@RequestParam(required = false) Long deptId,
                                         @RequestParam(required = false) String period) {
        return R.ok(headcountService.query(deptId, null, period));
    }
}
