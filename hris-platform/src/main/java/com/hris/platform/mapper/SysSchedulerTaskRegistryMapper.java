package com.hris.platform.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hris.platform.entity.SysSchedulerTaskRegistry;
import org.apache.ibatis.annotations.Mapper;

/**
 * 调度任务注册数据访问。
 */
@Mapper
public interface SysSchedulerTaskRegistryMapper extends BaseMapper<SysSchedulerTaskRegistry> {
}
