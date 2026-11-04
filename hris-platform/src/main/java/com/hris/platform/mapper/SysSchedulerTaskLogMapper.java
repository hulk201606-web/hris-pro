package com.hris.platform.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hris.platform.entity.SysSchedulerTaskLog;
import org.apache.ibatis.annotations.Mapper;

/** 调度任务执行日志数据访问。 */
@Mapper
public interface SysSchedulerTaskLogMapper extends BaseMapper<SysSchedulerTaskLog> {
}
