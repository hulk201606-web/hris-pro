package com.hris.recruit.controller;

import com.hris.common.api.R;
import com.hris.common.security.RequirePerm;
import com.hris.recruit.dto.PublicRecruitDtos.FunnelItem;
import com.hris.recruit.service.FunnelReportService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 招聘报表端点（RCS-F-020）：漏斗聚合，权限复用 rcu:requisition:read（报表属需求域读侧）。
 */
@RestController
@RequestMapping("/api/v1/rcu/reports")
public class RecruitReportController {

    private final FunnelReportService funnelReportService;

    public RecruitReportController(FunnelReportService funnelReportService) {
        this.funnelReportService = funnelReportService;
    }

    /**
     * 招聘漏斗：各阶段去重候选人数与相对 APPLIED 的转化率。
     *
     * @param from  起始时间（ISO，闭区间，可空）
     * @param to    截止时间（ISO，闭区间，可空）
     * @param reqId 需求单 ID（可空，不传为全量）
     */
    @RequirePerm("rcu:requisition:read")
    @GetMapping("/funnel")
    public R<List<FunnelItem>> funnel(
            @RequestParam(value = "from", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(value = "to", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
            @RequestParam(value = "reqId", required = false) Long reqId) {
        return R.ok(funnelReportService.funnel(from, to, reqId));
    }
}
