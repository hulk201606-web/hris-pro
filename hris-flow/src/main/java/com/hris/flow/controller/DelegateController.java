package com.hris.flow.controller;

import com.hris.common.api.R;
import com.hris.common.security.RequirePerm;
import com.hris.flow.dto.DelegateDtos.CreateRequest;
import com.hris.flow.dto.DelegateDtos.DelegateResponse;
import com.hris.flow.dto.DelegateDtos.MineResponse;
import com.hris.flow.service.DelegateService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 审批委托代理端点（FLW-F-009）。
 *
 * <p>写动作（创建/提前结束）使用 flow:task:write；GET /mine 为个人自助查询，
 * 照既有待办查询范式使用 flow:task:read（与任务中心 todo/done 口径一致）。</p>
 */
@RestController
@RequestMapping("/api/v1/flow/delegates")
public class DelegateController {

    private final DelegateService delegateService;

    public DelegateController(DelegateService delegateService) {
        this.delegateService = delegateService;
    }

    /** 创建委托：to_user≠from_user、end_at>start_at、同范围 ACTIVE 期不允许重叠。 */
    @RequirePerm("flow:task:write")
    @PostMapping
    public R<DelegateResponse> create(@Valid @RequestBody CreateRequest req) {
        return R.ok(delegateService.create(req));
    }

    /** 我的委托：我创建的 + 委托给我的，status 可选过滤（ACTIVE/ENDED）。 */
    @RequirePerm("flow:task:read")
    @GetMapping("/mine")
    public R<MineResponse> mine(@RequestParam(required = false) String status) {
        return R.ok(delegateService.mine(status));
    }

    /** 提前结束委托：仅委托发起人可操作。 */
    @RequirePerm("flow:task:write")
    @PostMapping("/{id}/end")
    public R<Void> end(@PathVariable Long id) {
        delegateService.end(id);
        return R.ok();
    }
}
