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

    /**
     * 编制校验（内部端点，供员工域入职/调动前置调用）：按当前季度计算缺口，
     * 返回 allowed 是否放行及 shortfall 缺口人数。
     */
    @GetMapping("/hc/check")
    public R<HcCheckResponse> check(@RequestParam("dept") Long dept,
                                    @RequestParam("position") Long position,
                                    @RequestParam("count") Integer count) {
        return R.ok(headcountService.check(dept, position, count));
    }

    /** 汇报链解析（内部端点）：返回某员工自上级起逐级向上的员工 ID 链，供 rag/权限数据范围使用。 */
    @GetMapping("/reportline/{empId}")
    public R<List<Long>> reportline(@PathVariable Long empId) {
        return R.ok(reportLineService.resolve(empId));
    }
}
