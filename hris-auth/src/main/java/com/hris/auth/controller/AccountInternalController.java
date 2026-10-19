package com.hris.auth.controller;

import com.hris.auth.dto.AccountDtos.AccountCreateRequest;
import com.hris.auth.dto.AccountDtos.AccountCreateResponse;
import com.hris.auth.service.AccountService;
import com.hris.common.api.R;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 账号内部端点（网关封禁外部访问，Feign 双向签名）。由 EMP 域调用。
 */
@RestController
@RequestMapping("/internal/auth")
public class AccountInternalController {

    private final AccountService accountService;

    public AccountInternalController(AccountService accountService) {
        this.accountService = accountService;
    }

    /**
     * 入职建号（内部端点，仅服务间 Feign 调用）：校验租户状态、检查用户名唯一、
     * Argon2id 加密初始密码、写入 auth_user 并分配角色。
     */
    @PostMapping("/accounts")
    public R<AccountCreateResponse> create(@Valid @RequestBody AccountCreateRequest req) {
        return R.ok(accountService.create(req));
    }

    /**
     * 离职禁用账号（内部端点）：按 tenantId + employeeId 定位账号，置 status=0 禁用。
     */
    @PutMapping("/accounts/{empId}/disable")
    public R<Void> disable(@PathVariable Long empId, @RequestParam Long tenantId) {
        accountService.disable(tenantId, empId);
        return R.ok();
    }
}
