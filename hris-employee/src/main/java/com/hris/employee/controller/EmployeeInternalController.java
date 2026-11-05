package com.hris.employee.controller;

import com.hris.common.api.R;
import com.hris.employee.dto.ApprovalDtos.ApprovalCallbackRequest;
import com.hris.employee.dto.ApprovalDtos.ApprovalCallbackResponse;
import com.hris.employee.dto.BlacklistCheckDtos.BlacklistCheckResponse;
import com.hris.employee.dto.EmployeeDtos.ProbationReminderItem;
import com.hris.employee.dto.PreCreateRequest;
import com.hris.employee.dto.PreHireLinkResponse;
import com.hris.employee.dto.ReportDtos.HeadcountStructureBriefRequest;
import com.hris.employee.dto.ReportDtos.HeadcountStructureBriefRow;
import com.hris.employee.service.BlacklistCheckService;
import com.hris.employee.service.EmployeeService;
import com.hris.employee.service.LifecycleCaseService;
import com.hris.employee.service.PendingTransactionService;
import com.hris.employee.service.PreHireService;
import com.hris.employee.service.ProbationReminderService;
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
import java.util.Map;

/**
 * 员工域内部端点（网关封禁外部访问，Feign 双向签名）。
 */
@RestController
@RequestMapping("/internal/emp")
public class EmployeeInternalController {

    private final EmployeeService employeeService;
    private final PreHireService preHireService;
    private final LifecycleCaseService lifecycleCaseService;
    private final PendingTransactionService pendingTransactionService;
    private final ProbationReminderService probationReminderService;
    private final com.hris.employee.service.EmploymentService employmentService;
    private final BlacklistCheckService blacklistCheckService;
    private final com.hris.employee.service.HeadcountStructureBriefService headcountStructureBriefService;

    public EmployeeInternalController(EmployeeService employeeService, PreHireService preHireService,
                                      LifecycleCaseService lifecycleCaseService,
                                      PendingTransactionService pendingTransactionService,
                                      ProbationReminderService probationReminderService,
                                      com.hris.employee.service.EmploymentService employmentService,
                                      BlacklistCheckService blacklistCheckService,
                                      com.hris.employee.service.HeadcountStructureBriefService headcountStructureBriefService) {
        this.employeeService = employeeService;
        this.preHireService = preHireService;
        this.lifecycleCaseService = lifecycleCaseService;
        this.pendingTransactionService = pendingTransactionService;
        this.probationReminderService = probationReminderService;
        this.employmentService = employmentService;
        this.blacklistCheckService = blacklistCheckService;
        this.headcountStructureBriefService = headcountStructureBriefService;
    }

    /** 员工档案简报（内部端点）：供 org 汇报线解析等跨域调用，返回姓名/工号/状态/部门/岗位/直属上级。 */
    @GetMapping("/{id}/profile-brief")
    public R<Map<String, Object>> profileBrief(@PathVariable Long id) {
        return R.ok(employeeService.profileBrief(id));
    }

    /** 生成待入职自助办理链接（内部端点）：为候选人预建档后生成一次性 pre-hire 链接。 */
    @PostMapping("/pre-hire-links")
    public R<PreHireLinkResponse> preHireLink(@RequestParam Long empId) {
        return R.ok(preHireService.createLink(empId));
    }

    /**
     * 候选人预建档（招聘域 Offer 录用后调用，docs/07 跨域契约）。
     * 仅创建员工主档（状态=待入职），部门/岗位/入职办理由后续 EMP 入职流程补全。
     */
    @PostMapping("/pre-create")
    public R<Long> preCreate(@RequestBody PreCreateRequest req) {
        return R.ok(employeeService.preCreate(req));
    }

    /**
     * 生命周期单审批结果回调（flow → emp，docs/19 §8.7-1）。
     * 幂等：单据已处终态时返回 changed=false 不重复落账。
     * 内部调用无 JWT，LifecycleCaseService.handleCallback 显式还原租户上下文并 finally 清理。
     */
    @PostMapping("/approval-callback")
    public R<ApprovalCallbackResponse> approvalCallback(
            @Valid @RequestBody ApprovalCallbackRequest req) {
        return R.ok(lifecycleCaseService.handleCallback(
                req.tenantId(), req.bizKey(), Boolean.TRUE.equals(req.approved()), req.comment()));
    }

    /**
     * 待生效事务批量落账（platform 调度中心 PENDING_TRANSACTION_APPLY 经 Feign 调用，docs/19 §9.1）。
     * 应用 effect_date <= effectDate 的全部 PENDING 事务（缺省今天）；幂等：非 PENDING 跳过。
     */
    @PostMapping("/pending-transactions/apply-due")
    public R<Integer> applyPendingTransactions(
            @RequestParam Long tenantId,
            @RequestParam(required = false) LocalDate effectDate) {
        return R.ok(pendingTransactionService.applyDue(tenantId,
                effectDate != null ? effectDate : LocalDate.now()));
    }

    /**
     * 试用期到期提醒（EMP-F-007 深化，platform 调度任务 PROBATION_REMINDER 经 Feign 拉取）。
     * 返回 probation_end 落在 [今天, 今天+windowDays] 的有效合同与员工摘要（缺省窗口 15 天）。
     */
    @GetMapping("/probation-reminders")
    public R<List<ProbationReminderItem>> probationReminders(
            @RequestParam(defaultValue = "15") int windowDays) {
        return R.ok(probationReminderService.reminders(windowDays));
    }

    /**
     * 按部门统计在职员工数（ORG-F-018 级联校验，org 删除部门前调用）：在职=现行雇佣关系。
     */
    @GetMapping("/count-active-by-dept")
    public R<Long> countActiveByDept(@RequestParam Long deptId) {
        return R.ok(employmentService.countActiveByDept(deptId));
    }

    /**
     * 人力结构简报批量落库（内部端点，P1 人力结构分析报表 / CMP-F-017 法人映射数据源）：
     * 返回员工×雇佣关系组织维度简报行（法人/部门/雇佣类型/在职状态码），无敏感字段。
     */
    @PostMapping("/headcount-structure-brief")
    public R<List<HeadcountStructureBriefRow>> headcountStructureBrief(
            @RequestBody HeadcountStructureBriefRequest req) {
        return R.ok(headcountStructureBriefService.rows(req == null ? null : req.asOf()));
    }

    /**
     * 黑名单校验（RCS-F-011 招聘域契约，Offer 录用/渠道导入前调用）：certNoHash
     * （SHA-256 小写 hex）与 mobile 至少提供其一，命中 blacklisted=1 员工即
     * blacklisted=true，reason 取 emp_status_log 最近一条或固定文案。
     */
    @GetMapping("/blacklist-check")
    public R<BlacklistCheckResponse> blacklistCheck(
            @RequestParam(required = false) String certNoHash,
            @RequestParam(required = false) String mobile) {
        return R.ok(blacklistCheckService.check(certNoHash, mobile));
    }
}
