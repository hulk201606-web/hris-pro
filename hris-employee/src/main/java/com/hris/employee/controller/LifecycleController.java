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

    @RequirePerm("emp:lifecycle:write")
    @PostMapping("/onboardings")
    public R<LifecycleCaseResponse> onboard(@Valid @RequestBody OnboardRequest req) {
        return R.ok(onboardingService.create(req));
    }

    @RequirePerm("emp:lifecycle:write")
    @PostMapping("/regularizations")
    public R<LifecycleCaseResponse> regularize(@Valid @RequestBody RegularizeRequest req) {
        return R.ok(regularizationService.create(req));
    }

    @RequirePerm("emp:lifecycle:write")
    @PostMapping("/transfers")
    public R<LifecycleCaseResponse> transfer(@Valid @RequestBody TransferRequest req) {
        return R.ok(transferService.create(req));
    }

    @RequirePerm("emp:lifecycle:write")
    @PostMapping("/resignations")
    public R<LifecycleCaseResponse> resign(@Valid @RequestBody ResignRequest req) {
        return R.ok(resignationService.create(req));
    }
}
