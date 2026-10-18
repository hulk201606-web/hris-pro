package com.hris.employee.integration;

import com.hris.common.api.R;
import com.hris.employee.client.AuthClient;
import com.hris.employee.dto.AccountDtos.AccountCreateRequest;
import com.hris.employee.dto.AccountDtos.AccountCreateResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 认证域集成门面（EMP-F-006 建号 / EMP-F-012 禁用）。
 * 失败降级不阻断主流程（docs/03-02 §2 账号联动为系统级事件，MVP 用直接调用 + 降级）。
 */
@Service
public class AccountIntegration {

    private static final Logger log = LoggerFactory.getLogger(AccountIntegration.class);

    private final AuthClient authClient;

    public AccountIntegration(AuthClient authClient) {
        this.authClient = authClient;
    }

    /**
     * 入职建号。返回 userId；auth 未就绪或失败时返回 null（降级，不阻断入职生效）。
     */
    public Long createAccount(Long tenantId, Long employeeId, String username,
                              String password, List<String> roleCodes) {
        if (username == null || username.isBlank() || password == null || password.isBlank()) {
            log.warn("入职建号参数缺失，跳过账号联动: empId={}", employeeId);
            return null;
        }
        try {
            R<AccountCreateResponse> resp = authClient.createAccount(
                    new AccountCreateRequest(tenantId, employeeId, username.trim().toLowerCase(), password, roleCodes));
            if (resp != null && resp.isSuccess() && resp.getData() != null) {
                return resp.getData().userId();
            }
            log.warn("入职建号失败: empId={}, code={}", employeeId, resp == null ? "null" : resp.getCode());
            return null;
        } catch (Exception e) {
            log.warn("auth 服务未就绪，入职建号降级跳过: empId={}, err={}", employeeId, e.getMessage());
            return null;
        }
    }

    /**
     * 离职禁用账号。失败降级（不阻断离职落账，后续经对账补偿）。
     */
    public void disable(Long tenantId, Long employeeId) {
        try {
            authClient.disable(employeeId, tenantId);
        } catch (Exception e) {
            log.warn("auth 服务未就绪，离职禁用账号降级跳过: empId={}, err={}", employeeId, e.getMessage());
        }
    }
}
