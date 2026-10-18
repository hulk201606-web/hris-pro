package com.hris.flow.controller;

import com.hris.common.api.R;
import com.hris.common.security.RequirePerm;
import com.hris.flow.dto.StartInstanceRequest;
import com.hris.flow.dto.StartInstanceResponse;
import com.hris.flow.dto.TrackResponse;
import com.hris.flow.service.InstanceService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 流程实例端点（发起/撤回/轨迹）。
 */
@RestController
@RequestMapping("/api/v1/flow")
public class InstanceController {

    private final InstanceService instanceService;

    public InstanceController(InstanceService instanceService) {
        this.instanceService = instanceService;
    }

    @RequirePerm("flow:instance:start")
    @PostMapping("/instances")
    public R<StartInstanceResponse> start(@Valid @RequestBody StartInstanceRequest req) {
        return R.ok(instanceService.start(req));
    }

    @DeleteMapping("/instances/{id}")
    public R<Void> withdraw(@PathVariable("id") String id,
                            @RequestParam(value = "reason", required = false) String reason) {
        instanceService.withdraw(id, reason);
        return R.ok();
    }

    @GetMapping("/instances/{id}/track")
    public R<TrackResponse> track(@PathVariable("id") String id) {
        return R.ok(instanceService.track(id));
    }
}
