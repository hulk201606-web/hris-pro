package com.hris.org.controller;

import com.hris.common.api.R;
import com.hris.org.dto.HcCheckResponse;
import com.hris.org.service.HeadcountService;
import com.hris.org.service.ReportLineService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 组织域内部端点（网关封禁外部访问，Feign 双向签名）。
 */
@RestController
@RequestMapping("/internal/org")
public class OrgInternalController {

    private final HeadcountService headcountService;
    private final ReportLineService reportLineService;

    public OrgInternalController(HeadcountService headcountService, ReportLineService reportLineService) {
        this.headcountService = headcountService;
        this.reportLineService = reportLineService;
    }

    @GetMapping("/hc/check")
    public R<HcCheckResponse> check(@RequestParam("dept") Long dept,
                                    @RequestParam("position") Long position,
                                    @RequestParam("count") Integer count) {
        return R.ok(headcountService.check(dept, position, count));
    }

    @GetMapping("/reportline/{empId}")
    public R<List<Long>> reportline(@PathVariable Long empId) {
        return R.ok(reportLineService.resolve(empId));
    }
}
