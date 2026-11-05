package com.hris.attendance.controller;

import com.hris.attendance.dto.LeaveQuotaDtos.LeaveLedgerResponse;
import com.hris.attendance.dto.LeaveQuotaDtos.LeaveQuotaResponse;
import com.hris.attendance.dto.LeaveQuotaDtos.QuotaBalanceResponse;
import com.hris.attendance.dto.LeaveQuotaDtos.QuotaGrantRequest;
import com.hris.attendance.dto.LeaveQuotaDtos.QuotaTransferRequest;
import com.hris.attendance.service.LeaveQuotaService;
import com.hris.common.api.R;
import com.hris.common.exception.BizException;
import com.hris.common.exception.ErrorCode;
import com.hris.common.security.AuthContextHolder;
import com.hris.common.security.RequirePerm;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 假期额度端点（ATT-F-012 / ATT-F-013 / ATT-F-015）。
 */
@RestController
@RequestMapping("/api/v1/att/leave-quotas")
public class LeaveQuotaController {

    private final LeaveQuotaService leaveQuotaService;

    public LeaveQuotaController(LeaveQuotaService leaveQuotaService) {
        this.leaveQuotaService = leaveQuotaService;
    }

    @GetMapping
    public R<List<LeaveQuotaResponse>> query(@RequestParam(required = false) Long empId,
                                             @RequestParam(required = false) String period) {
        return R.ok(leaveQuotaService.query(empId, period));
    }

    /** 员工本人余额自助视图；empId 缺省时取网关透传的当前用户 ID。 */
    @GetMapping("/me")
    public R<QuotaBalanceResponse> me(@RequestParam(required = false) Long empId) {
        Long target = empId == null ? AuthContextHolder.getUserId() : empId;
        if (target == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "无法识别当前员工身份");
        }
        return R.ok(leaveQuotaService.balance(target));
    }

    @GetMapping("/ledgers")
    public R<List<LeaveLedgerResponse>> ledgers(@RequestParam(required = false) Long empId,
                                                @RequestParam(required = false) Long leaveTypeId) {
        return R.ok(leaveQuotaService.ledgers(empId, leaveTypeId));
    }

    /** 当前员工假期台账自助视图；照 /me 口径取网关透传的当前用户 ID，避免全租户流水泄露。 */
    @GetMapping("/me/ledgers")
    public R<List<LeaveLedgerResponse>> myLedgers(@RequestParam(required = false) Long leaveTypeId) {
        Long target = AuthContextHolder.getUserId();
        if (target == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "无法识别当前员工身份");
        }
        return R.ok(leaveQuotaService.ledgers(target, leaveTypeId));
    }

    @RequirePerm("att:leave:quota:write")
    @PostMapping("/grant")
    public R<LeaveQuotaResponse> grant(@Valid @RequestBody QuotaGrantRequest req) {
        return R.ok(leaveQuotaService.grant(req));
    }

    @RequirePerm("att:leave:quota:write")
    @PostMapping("/transfer")
    public R<LeaveQuotaResponse> transfer(@Valid @RequestBody QuotaTransferRequest req) {
        return R.ok(leaveQuotaService.transfer(req));
    }
}
