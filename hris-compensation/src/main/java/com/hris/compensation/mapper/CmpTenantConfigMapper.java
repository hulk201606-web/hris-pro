package com.hris.compensation.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 租户配置读取（PLT 配置中心 sys_tenant_config，广播表：按 docs/12 全片复制，
 * 各域可读本片副本，写入仍归 hris-platform）。
 *
 * <p>租户隔离：tenant_id 条件由 MyBatis-Plus TenantLineInnerInterceptor 自动追加，
 * 业务代码只按 config_key 查询。</p>
 */
@Mapper
public interface CmpTenantConfigMapper {

    /** 按配置键读取当前租户配置值；不存在返回 null。 */
    @Select("SELECT config_value FROM sys_tenant_config WHERE config_key = #{configKey} AND deleted = 0 LIMIT 1")
    String selectValue(@Param("configKey") String configKey);
}
