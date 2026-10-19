package com.hris.auth.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hris.auth.dto.AccountDtos.AccountCreateRequest;
import com.hris.auth.dto.AccountDtos.AccountCreateResponse;
import com.hris.auth.entity.AuthRole;
import com.hris.auth.entity.AuthTenant;
import com.hris.auth.entity.AuthUser;
import com.hris.auth.entity.AuthUserRole;
import com.hris.auth.mapper.AuthRoleMapper;
import com.hris.auth.mapper.AuthTenantMapper;
import com.hris.auth.mapper.AuthUserMapper;
import com.hris.auth.mapper.AuthUserRoleMapper;
import com.hris.common.audit.AuditLog;
import com.hris.common.exception.BizException;
import com.hris.common.exception.ErrorCode;
import com.hris.common.trace.TraceContext;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 账号生命周期（内部接口，由 EMP 域经 Feign 调用）：入职建号 / 离职禁用。
 */
@Service
public class AccountService {

    private static final int STATUS_ENABLED = 1;
    private static final int STATUS_DISABLED = 0;

    private final AuthTenantMapper tenantMapper;
    private final AuthUserMapper userMapper;
    private final AuthRoleMapper roleMapper;
    private final AuthUserRoleMapper userRoleMapper;
    private final PasswordEncoder passwordEncoder;

    public AccountService(AuthTenantMapper tenantMapper, AuthUserMapper userMapper,
                          AuthRoleMapper roleMapper, AuthUserRoleMapper userRoleMapper,
                          PasswordEncoder passwordEncoder) {
        this.tenantMapper = tenantMapper;
        this.userMapper = userMapper;
        this.roleMapper = roleMapper;
        this.userRoleMapper = userRoleMapper;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * 入职建号（内部 Feign 调用）：
     * <ol>
     *   <li>校验租户存在且状态=正常（findByIdIgnoreTenant 绕过租户插件）；</li>
     *   <li>临时设置 TraceContext.tenantId（内部调用无请求头上下文），finally 清理；</li>
     *   <li>用户名小写化后查重，已存在抛 10001；</li>
     *   <li>Argon2id 加密初始密码，插入 auth_user；</li>
     *   <li>按 roleCodes 匹配角色并写入 auth_user_role 关联。</li>
     * </ol>
     */
    @AuditLog(value = "入职建号", module = "auth")
    public AccountCreateResponse create(AccountCreateRequest req) {
        AuthTenant tenant = tenantMapper.findByIdIgnoreTenant(req.tenantId());
        if (tenant == null || tenant.getStatus() == null || tenant.getStatus() != STATUS_ENABLED) {
            throw new BizException(ErrorCode.TENANT_INVALID, "租户不存在或已停用");
        }
        TraceContext.setTenantId(req.tenantId());
        TraceContext.setUserId(0L);
        try {
            String username = req.username().trim().toLowerCase();
            Long cnt = userMapper.selectCount(
                    new LambdaQueryWrapper<AuthUser>().eq(AuthUser::getUsername, username));
            if (cnt != null && cnt > 0) {
                throw new BizException(ErrorCode.PARAM_INVALID, "用户名已存在");
            }
            AuthUser user = new AuthUser();
            user.setEmployeeId(req.employeeId());
            user.setUsername(username);
            user.setPwdHash(passwordEncoder.encode(req.password()));
            user.setStatus(STATUS_ENABLED);
            userMapper.insert(user);
            assignRoles(user.getId(), req.roleCodes());
            return new AccountCreateResponse(user.getId(), req.tenantId(), req.employeeId());
        } finally {
            TraceContext.setTenantId(null);
            TraceContext.setUserId(null);
        }
    }

    /**
     * 离职禁用账号：临时设置 TraceContext.tenantId，按 employeeId 查账号，置 status=0。
     * 账号不存在抛 10002 NOT_FOUND。
     */
    @AuditLog(value = "离职禁用账号", module = "auth")
    public void disable(Long tenantId, Long employeeId) {
        TraceContext.setTenantId(tenantId);
        try {
            AuthUser user = userMapper.selectOne(new LambdaQueryWrapper<AuthUser>()
                    .eq(AuthUser::getEmployeeId, employeeId));
            if (user == null) {
                throw new BizException(ErrorCode.NOT_FOUND, "账号不存在");
            }
            user.setStatus(STATUS_DISABLED);
            userMapper.updateById(user);
        } finally {
            TraceContext.setTenantId(null);
        }
    }

    /**
     * 按角色编码集合匹配 auth_role，逐条写入 auth_user_role 关联。
     * roleCodes 为空时跳过（不分配任何角色）。
     */
    private void assignRoles(Long userId, List<String> roleCodes) {
        if (roleCodes == null || roleCodes.isEmpty()) {
            return;
        }
        List<AuthRole> roles = roleMapper.selectList(
                new LambdaQueryWrapper<AuthRole>().in(AuthRole::getCode, roleCodes));
        for (AuthRole role : roles) {
            AuthUserRole ur = new AuthUserRole();
            ur.setUserId(userId);
            ur.setRoleId(role.getId());
            userRoleMapper.insert(ur);
        }
    }
}
