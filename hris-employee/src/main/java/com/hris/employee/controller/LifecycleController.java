package com.hris.employee.controller;

import com.hris.common.api.R;
import com.hris.common.security.RequirePerm;
import com.hris.employee.dto.LifecycleCaseResponse;
import com.hris.employee.dto.OnboardRequest;
import com.hris.employee.dto.RegularizeRequest;
import com.hris.employee.dto.ResignRequest;
import com.hris.employee.dto.TransferRequest;
import com.hris.employee.service.OnboardingService;
import com.hris.employee.service.RegularizationService;
import com.hris.employee.service.ResignationService;
import com.hris.employee.service.TransferService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 生命周期端点（入职/转正/调动/离职，各创建单并发起对应流程）。
 */
@RestController
@RequestMapping("/api/v1/emp")
public class LifecycleController {

    private final OnboardingService onboardingService;
    private final RegularizationService regularizationService;
    private final TransferService transferService;
    private final ResignationService resignationService;

    public LifecycleController(OnboardingService onboardingService, RegularizationService regularizationService,
                               TransferService transferService, ResignationService resignationService) {
        this.onboardingService = onboardingService;
        this.regularizationService = regularizationService;
        this.transferService = transferService;
        this.resignationService = resignationService;
    }

    /**
     * 发起入职办理（权限点 emp:lifecycle:write）：仅待入职员工可发起；先做编制校验，
     * 再创建 EMP_ONBOARD 生命周期单并驱动流程引擎，载荷含法人/部门/岗位/职级/直属上级/初始账号。
     */
    @RequirePerm("emp:lifecycle:write")
    @PostMapping("/onboardings")
    public R<LifecycleCaseResponse> onboard(@Valid @RequestBody OnboardRequest req) {
        return R.ok(onboardingService.create(req));
    }

    /**
     * 发起转正办理（权限点 emp:lifecycle:write）：试用→正式的状态流转入口，创建
     * EMP_REGULARIZE 生命周期单，转正生效后由状态机落正式在职。
     */
    @RequirePerm("emp:lifecycle:write")
    @PostMapping("/regularizations")
    public R<LifecycleCaseResponse> regularize(@Valid @RequestBody RegularizeRequest req) {
        return R.ok(regularizationService.create(req));
    }

    /**
     * 发起调动办理（权限点 emp:lifecycle:write）：仅在职员工可发起；校验目标部门/岗位编制，
     * 创建 EMP_TRANSFER 单，调动生效日经待生效事务落账，员工主状态不变。
     */
    @RequirePerm("emp:lifecycle:write")
    @PostMapping("/transfers")
    public R<LifecycleCaseResponse> transfer(@Valid @RequestBody TransferRequest req) {
        return R.ok(transferService.create(req));
    }

    /**
     * 发起离职办理（权限点 emp:lifecycle:write）：在职/试用员工可发起；创建 EMP_RESIGN 单，
     * 流程通过后进入"离职中"，最后工作日落账后转"已离职"终态。
     */
    @RequirePerm("emp:lifecycle:write")
    @PostMapping("/resignations")
    public R<LifecycleCaseResponse> resign(@Valid @RequestBody ResignRequest req) {
        return R.ok(resignationService.create(req));
    }
}
