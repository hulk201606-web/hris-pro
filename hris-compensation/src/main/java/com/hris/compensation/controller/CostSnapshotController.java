package com.hris.compensation.controller;

import com.hris.common.api.R;
import com.hris.common.security.RequirePerm;
import com.hris.compensation.dto.CostSnapshotDtos.CostSnapshotRow;
import com.hris.compensation.dto.CostSnapshotDtos.RefreshResponse;
import com.hris.compensation.service.CostSnapshotService;
import com.hris.compensation.service.LegalEntityCostReportService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 法人成本快照端点（批 12 #29：聚合库物化）。
 *
 * <p><b>契约</b></p>
 * <ul>
 *   <li>{@code POST /api/v1/cmp/reports/cost-snapshot/refresh?periodCode=}（payroll write）：
 *       按实时口径重算该期并 upsert 快照，返回 {periodCode, rows, affected, refreshedAt}；</li>
 *   <li>{@code GET /api/v1/cmp/reports/cost-snapshot?periodCode=}（payroll read）：
 *       读快照（走 aggregate 数据源），返回法人级汇总 + refreshedAt。</li>
 * </ul>
 *
 * <p><b>调度</b>：刷新为幂等全量覆盖，由 platform 调度中心注册<b>月度</b>任务
 * （建议每月 1 日 02:00，紧接上月关账），本模块不内置 @Scheduled。</p>
 *
 * <p>权限口径与报表一致：只涉及法人级汇总，不返回单人明细或密文，故挂 payroll 读写权限码，
 * 不要求 cmp:salary:read 密域权限；但写入方（刷新）会改变报表数据，按写权限收口。</p>
 */
@RestController
@RequestMapping("/api/v1/cmp/reports/cost-snapshot")
public class CostSnapshotController {

    private final CostSnapshotService snapshotService;
    private final LegalEntityCostReportService reportService;

    public CostSnapshotController(CostSnapshotService snapshotService,
                                 LegalEntityCostReportService reportService) {
        this.snapshotService = snapshotService;
        this.reportService = reportService;
    }

    /** 重算并物化该期快照（幂等：重复执行以最后一次为准）。 */
    @RequirePerm("cmp:payroll-detail:write")
    @PostMapping("/refresh")
    public R<RefreshResponse> refresh(@RequestParam String periodCode) {
        return R.ok(snapshotService.refresh(periodCode, reportService.legalEntityCost(periodCode)));
    }

    /** 读快照（聚合数据源）。 */
    @RequirePerm("cmp:payroll-detail:read")
    @GetMapping
    public R<List<CostSnapshotRow>> list(@RequestParam String periodCode) {
        return R.ok(snapshotService.list(periodCode));
    }
}
