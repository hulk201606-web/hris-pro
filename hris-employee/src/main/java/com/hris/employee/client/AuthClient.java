package com.hris.employee.client;

import com.hris.common.api.R;
import com.hris.employee.dto.AccountDtos.AccountCreateRequest;
import com.hris.employee.dto.AccountDtos.AccountCreateResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * 认证域 Feign 客户端（docs/07 内部契约：入职建号 / 离职禁用）。
 */
@FeignClient(name = "hris-auth", path = "/internal/auth")
public interface AuthClient {

    /**
     * 入职建号（EMP-F-006 账号联动开通）。
     */
    @PostMapping("/accounts")
    R<AccountCreateResponse> createAccount(@RequestBody AccountCreateRequest req);

    /**
     * 离职禁用账号（EMP-F-012）。
     */
    @PutMapping("/accounts/{empId}/disable")
    R<Void> disable(@PathVariable("empId") Long empId, @RequestParam("tenantId") Long tenantId);
}
