package com.hris.compensation.controller;

import com.hris.common.api.R;
import com.hris.common.dto.PageResult;
import com.hris.common.security.RequirePerm;
import com.hris.compensation.dto.PeriodLockDtos.PeriodLockResponse;
import com.hris.compensation.dto.PeriodLockDtos.PeriodUnlockRequest;
import com.hris.compensation.service.PeriodLockService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 期间关账端点（CMP-F-020）。复用 payroll 读写权限码，不新增权限点。
 */
@RestController
@RequestMapping("/api/v1/cmp/period-locks")
public class PeriodLockController {

    private final PeriodLockService service;

    public PeriodLockController(PeriodLockService service) {
        this.service = service;
    }

    /** 关账：该期间无 ACTIVE 锁才可锁，重复关账拒绝。 */
    @RequirePerm("cmp:payroll-run:write")
    @PostMapping("/{periodCode}/lock")
    public R<PeriodLockResponse> lock(@PathVariable String periodCode) {
        return R.ok(service.lock(periodCode));
    }

    /** 解锁：理由必填（≥4 字），ACTIVE 锁置 RELEASED 并留痕。 */
    @RequirePerm("cmp:payroll-run:write")
    @PostMapping("/{periodCode}/unlock")
    public R<PeriodLockResponse> unlock(@PathVariable String periodCode,
                                        @Valid @RequestBody PeriodUnlockRequest req) {
        return R.ok(service.unlock(periodCode, req.reason()));
    }

    /** 分页查询关账记录（active=true 只看生效锁，periodCode 可选过滤）。 */
    @RequirePerm("cmp:payroll-run:read")
    @GetMapping
    public R<PageResult<PeriodLockResponse>> list(
            @RequestParam(required = false) Boolean active,
            @RequestParam(required = false) String periodCode,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long size) {
        return R.ok(service.list(active, periodCode, page, size));
    }
}
