package com.hris.platform.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hris.platform.entity.SysTenantConfig;
import org.apache.ibatis.annotations.Mapper;

/**
 * 租户参数数据访问。
 */
@Mapper
public interface SysTenantConfigMapper extends BaseMapper<SysTenantConfig> {
}
