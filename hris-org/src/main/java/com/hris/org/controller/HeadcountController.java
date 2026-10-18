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

    @GetMapping("/headcounts")
    public R<List<HeadcountUsage>> list(@RequestParam(required = false) Long deptId,
                                        @RequestParam(required = false) Long positionId,
                                        @RequestParam(required = false) String period) {
        return R.ok(headcountService.query(deptId, positionId, period));
    }

    @RequirePerm("org:headcount:write")
    @PutMapping("/headcounts")
    public R<HeadcountUsage> set(@Valid @RequestBody HeadcountRequest req) {
        return R.ok(headcountService.set(req));
    }

    @GetMapping("/headcounts/usage")
    public R<List<HeadcountUsage>> usage(@RequestParam(required = false) Long deptId,
                                         @RequestParam(required = false) String period) {
        return R.ok(headcountService.query(deptId, null, period));
    }
}
