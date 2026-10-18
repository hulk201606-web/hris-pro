package com.hris.auth.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hris.auth.entity.AuthDataScope;
import com.hris.auth.entity.AuthRole;
import com.hris.auth.mapper.AuthDataScopeMapper;
import com.hris.auth.mapper.AuthRoleMapper;
import com.hris.auth.mapper.AuthRolePermMapper;
import com.hris.auth.mapper.AuthUserRoleMapper;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 用户授权信息读取（角色 / 权限点 / 数据权限）。纯读取，无事务、无 HTTP 依赖。
 */
@Service
public class UserAuthService {

    private final AuthUserRoleMapper userRoleMapper;
    private final AuthRoleMapper roleMapper;
    private final AuthRolePermMapper rolePermMapper;
    private final AuthDataScopeMapper dataScopeMapper;

    public UserAuthService(AuthUserRoleMapper userRoleMapper, AuthRoleMapper roleMapper,
                           AuthRolePermMapper rolePermMapper, AuthDataScopeMapper dataScopeMapper) {
        this.userRoleMapper = userRoleMapper;
        this.roleMapper = roleMapper;
        this.rolePermMapper = rolePermMapper;
        this.dataScopeMapper = dataScopeMapper;
    }

    public List<Long> roleIdsOf(Long userId) {
        return userRoleMapper.selectRoleIdsByUserId(userId);
    }

    public List<AuthRole> rolesOf(Long userId) {
        List<Long> roleIds = roleIdsOf(userId);
        if (roleIds.isEmpty()) {
            return List.of();
        }
        return roleMapper.selectByIds(roleIds);
    }

    public List<String> permsOf(Long userId) {
        List<Long> roleIds = roleIdsOf(userId);
        if (roleIds.isEmpty()) {
            return List.of();
        }
        return rolePermMapper.selectPermCodesByRoleIds(roleIds);
    }

    public List<AuthDataScope> dataScopesOf(Long userId) {
        List<Long> roleIds = roleIdsOf(userId);
        if (roleIds.isEmpty()) {
            return List.of();
        }
        return dataScopeMapper.selectList(
                new LambdaQueryWrapper<AuthDataScope>().in(AuthDataScope::getRoleId, roleIds));
    }
}
