package com.hris.recruit.mapper;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hris.recruit.entity.SiteDomain;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 官网租户域名映射数据访问。
 *
 * <p>公开链路按域名解析租户时尚无租户上下文（在 TraceContext.setTenantId 之前），
 * 查询必须 {@code @InterceptorIgnore(tenantLine = "true")} 绕过租户插件（与
 * AuthTenantMapper.findByDomain 同款范式）。管理端 CRUD 走 BaseMapper 通用方法，
 * 由租户插件按上下文隔离。</p>
 */
@Mapper
public interface SiteDomainMapper extends BaseMapper<SiteDomain> {

    /** 按域名查启用映射（跨租户，公开链路专用）；未命中返回 null。 */
    @InterceptorIgnore(tenantLine = "true")
    @Select("SELECT * FROM rcu_site_domain WHERE domain = #{domain} AND status = 'ENABLED' "
            + "AND deleted = 0 LIMIT 1")
    SiteDomain findEnabledByDomain(@Param("domain") String domain);
}
