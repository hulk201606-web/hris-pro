package com.hris.employee.controller;

import com.hris.common.api.R;
import com.hris.employee.dto.ApprovalDtos.ApprovalCallbackRequest;
import com.hris.employee.dto.ApprovalDtos.ApprovalCallbackResponse;
import com.hris.employee.dto.PreCreateRequest;
import com.hris.employee.dto.PreHireLinkResponse;
import com.hris.employee.service.EmployeeService;
import com.hris.employee.service.LifecycleCaseService;
import com.hris.employee.service.PendingTransactionService;
import com.hris.employee.service.PreHireService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
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

    public EmployeeInternalController(EmployeeService employeeService, PreHireService preHireService,
                                      LifecycleCaseService lifecycleCaseService,
                                      PendingTransactionService pendingTransactionService) {
        this.employeeService = employeeService;
        this.preHireService = preHireService;
        this.lifecycleCaseService = lifecycleCaseService;
        this.pendingTransactionService = pendingTransactionService;
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
}
