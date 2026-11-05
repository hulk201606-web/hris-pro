package com.hris.employee.controller;

import com.hris.common.api.R;
import com.hris.common.exception.BizException;
import com.hris.common.exception.ErrorCode;
import com.hris.common.security.AuthContextHolder;
import com.hris.common.security.RequirePerm;
import com.hris.employee.dto.SelfDtos.CriticalChangeRequest;
import com.hris.employee.dto.SelfDtos.CriticalChangeResponse;
import com.hris.employee.dto.SelfDtos.SelfDashboardResponse;
import com.hris.employee.dto.SelfDtos.SelfProfileResponse;
import com.hris.employee.dto.SelfDtos.SelfProfileUpdateRequest;
import com.hris.employee.service.SelfDashboardService;
import com.hris.employee.service.SelfProfileService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 员工自助端点（SELF-F-001 / SELF-F-002）。
 *
 * <p>复用既有员工读写权限码（本仓无独立 self 权限码）：读用
 * {@code emp:employee:read}，写用 {@code emp:employee:write}。所有端点只操作
 * 当前登录用户（X-User-Id，与 empId 同源），不接受路径参数，避免越权读他人档案。</p>
 */
@RestController
@RequestMapping("/api/v1/emp/me")
public class SelfController {

    private final SelfProfileService profileService;
    private final SelfDashboardService dashboardService;

    public SelfController(SelfProfileService profileService, SelfDashboardService dashboardService) {
        this.profileService = profileService;
        this.dashboardService = dashboardService;
    }

    /** 我的信息（证件/手机仅掩码）。 */
    @RequirePerm("emp:employee:read")
    @GetMapping("/profile")
    public R<SelfProfileResponse> profile() {
        return R.ok(profileService.profile(currentEmpId()));
    }

    /**
     * 自助信息变更：非关键字段直改生效。
     * 关键字段（证件号/证件类型/银行卡/雇佣类型）返回 PARAM_INVALID，提示 EMP_PROFILE_CHG 流程。
     */
    @RequirePerm("emp:employee:write")
    @PatchMapping("/profile")
    public R<SelfProfileResponse> update(@RequestBody SelfProfileUpdateRequest req) {
        return R.ok(profileService.update(currentEmpId(), req));
    }

    /** 发起关键信息变更申请（EMP_PROFILE_CHG），返回 bizKey 供前端跟踪审批。 */
    @RequirePerm("emp:employee:write")
    @PostMapping("/profile/change-requests")
    public R<CriticalChangeResponse> requestChange(@RequestBody CriticalChangeRequest req) {
        return R.ok(profileService.requestChange(currentEmpId(), req));
    }

    /** 自助首页聚合（个人资料/考勤/假期/工资条/待办），子项独立 fail-open。 */
    @RequirePerm("emp:employee:read")
    @GetMapping("/dashboard")
    public R<SelfDashboardResponse> dashboard() {
        return R.ok(dashboardService.load(currentEmpId()));
    }

    private Long currentEmpId() {
        Long empId = AuthContextHolder.getUserId();
        if (empId == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "无法识别当前员工身份");
        }
        return empId;
    }
}
