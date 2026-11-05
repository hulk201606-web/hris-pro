package com.hris.org.controller;

import com.hris.common.api.R;
import com.hris.common.trace.TraceContext;
import com.hris.org.dto.ApprovalDtos.ApprovalCallbackRequest;
import com.hris.org.dto.ApprovalDtos.ApprovalCallbackResponse;
import com.hris.org.dto.HcCheckResponse;
import com.hris.org.dto.OccupancyRequest;
import com.hris.org.dto.OrgVersionDtos.ActivateDueResponse;
import com.hris.org.dto.ReportDtos.LegalEntityBriefItem;
import com.hris.org.dto.ReportDtos.LegalEntityBriefRequest;
import com.hris.org.service.DeptService;
import com.hris.org.service.HeadcountService;
import com.hris.org.service.LegalEntityService;
import com.hris.org.service.OrgVersionStateService;
import com.hris.org.service.ReportLineService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/**
 * 组织域内部端点（网关封禁外部访问，Feign 双向签名）。
 */
@RestController
@RequestMapping("/internal/org")
public class OrgInternalController {

    private final HeadcountService headcountService;
    private final ReportLineService reportLineService;
    private final DeptService deptService;
    private final LegalEntityService legalEntityService;
    private final OrgVersionStateService versionStateService;

    public OrgInternalController(HeadcountService headcountService, ReportLineService reportLineService,
                                 DeptService deptService, LegalEntityService legalEntityService,
                                 OrgVersionStateService versionStateService) {
        this.headcountService = headcountService;
        this.reportLineService = reportLineService;
        this.deptService = deptService;
        this.legalEntityService = legalEntityService;
        this.versionStateService = versionStateService;
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

    /**
     * 编制占用回写（§10.5-B8，emp 入转调离落账经 Feign 调用）：按 transNo 幂等，
     * delta=+1 占用 / -1 释放，period 为空按当前季度。
     */
    @PostMapping("/headcounts/occupancy")
    public R<Void> occupancy(@Valid @RequestBody OccupancyRequest req) {
        headcountService.occupancy(req);
        return R.ok();
    }

    /** 汇报链解析（内部端点）：返回某员工自上级起逐级向上的员工 ID 链，供 rag/权限数据范围使用。 */
    @GetMapping("/reportline/{empId}")
    public R<List<Long>> reportline(@PathVariable Long empId) {
        return R.ok(reportLineService.resolve(empId));
    }

    /**
     * 法人简报批量查询（内部端点，CMP-F-017 多法人成本合并报表名称富化）：
     * 按 id 集合返回 {id, name}，不存在的 id 静默跳过。
     */
    @PostMapping("/legal-entities/brief")
    public R<List<LegalEntityBriefItem>> legalEntityBrief(@Valid @RequestBody LegalEntityBriefRequest req) {
        return R.ok(legalEntityService.brief(req.ids()));
    }

    /**
     * 组织版本到期激活（批 11 #23，供 platform 调度中心日任务调用，docs/20 §4.3）：
     * 激活本租户 DRAFT 且 effective_from &lt;= effectiveDate 的批次，原 ACTIVE 置 SUPERSEDED。
     * 调度线程无 JWT，租户上下文需显式还原（版本表受租户插件拦截），finally 清理。
     *
     * @param tenantId      租户（缺省取透传上下文）
     * @param effectiveDate 判定基准日 ISO yyyy-MM-dd（缺省今天）
     */
    @PostMapping("/versions/activate-due")
    public R<ActivateDueResponse> activateDueVersions(
            @RequestParam(value = "tenantId", required = false) Long tenantId,
            @RequestParam(value = "effectiveDate", required = false) String effectiveDate) {
        Long ctx = tenantId != null ? tenantId : TraceContext.getTenantId();
        LocalDate date = effectiveDate == null || effectiveDate.isBlank()
                ? LocalDate.now() : LocalDate.parse(effectiveDate);
        TraceContext.setTenantId(ctx);
        try {
            return R.ok(new ActivateDueResponse(date, versionStateService.activateDue(date)));
        } finally {
            TraceContext.setTenantId(null);
        }
    }

    /**
     * 审批结果回调（flow → org，docs/19 §8.7-1）：幂等更新 ORG_ADJUST 申请单状态，
     * 审批通过且 payload 含结构化变更动作时应用变更。
     * 内部调用无 JWT，须显式还原租户上下文（org_dept_history 受租户插件拦截），finally 清理。
     */
    @PostMapping("/approval-callback")
    public R<ApprovalCallbackResponse> approvalCallback(@Valid @RequestBody ApprovalCallbackRequest req) {
        Long tenantId = req.tenantId() != null ? req.tenantId() : TraceContext.getTenantId();
        TraceContext.setTenantId(tenantId);
        try {
            return R.ok(deptService.handleAdjustCallback(
                    req.bizKey(), Boolean.TRUE.equals(req.approved()), req.comment()));
        } finally {
            TraceContext.setTenantId(null);
        }
    }
}
