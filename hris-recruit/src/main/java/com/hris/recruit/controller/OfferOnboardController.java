package com.hris.recruit.controller;

import com.hris.common.api.R;
import com.hris.common.security.RequirePerm;
import com.hris.recruit.service.OfferOnboardService;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Offer 录用建档（RCU-F-030，跨域：招聘 → 员工）。
 */
@RestController
@RequestMapping("/api/v1/rcu")
public class OfferOnboardController {

    private final OfferOnboardService offerOnboardService;

    public OfferOnboardController(OfferOnboardService offerOnboardService) {
        this.offerOnboardService = offerOnboardService;
    }

    /**
     * Offer 录用并触发员工建档（权限点 rcu:offer:onboard）：跨域调用员工域预建档接口，
     * 成功后置 offer=ACCEPTED、candidate=HIRED；已录用则拒绝重复建档（幂等）。返回新员工 ID。
     */
    @PostMapping("/offers/{id}/onboard")
    @RequirePerm("rcu:offer:onboard")
    public R<Long> onboard(@PathVariable("id") Long offerId) {
        return R.ok(offerOnboardService.acceptAndOnboard(offerId));
    }
}
