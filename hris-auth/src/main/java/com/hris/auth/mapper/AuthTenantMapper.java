package com.hris.auth.mapper;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hris.auth.entity.AuthTenant;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 租户主档数据访问。auth_tenant 为平台级表（无 tenant_id），
 * 查询必须 {@code @InterceptorIgnore(tenantLine = "true")} 绕过租户插件。
 */
@Mapper
public interface AuthTenantMapper extends BaseMapper<AuthTenant> {

    @InterceptorIgnore(tenantLine = "true")
    @Select("SELECT * FROM auth_tenant WHERE code = #{code} AND deleted = 0 LIMIT 1")
    AuthTenant findByCode(@Param("code") String code);

    @InterceptorIgnore(tenantLine = "true")
    @Select("SELECT * FROM auth_tenant WHERE domain = #{domain} AND deleted = 0 LIMIT 1")
    AuthTenant findByDomain(@Param("domain") String domain);

    @InterceptorIgnore(tenantLine = "true")
    @Select("SELECT * FROM auth_tenant WHERE id = #{id} AND deleted = 0 LIMIT 1")
    AuthTenant findByIdIgnoreTenant(@Param("id") Long id);
}
