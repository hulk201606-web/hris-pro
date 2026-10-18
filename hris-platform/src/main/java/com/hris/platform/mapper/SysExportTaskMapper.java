package com.hris.platform.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hris.platform.entity.SysExportTask;
import org.apache.ibatis.annotations.Mapper;

/**
 * 异步导出任务数据访问。
 */
@Mapper
public interface SysExportTaskMapper extends BaseMapper<SysExportTask> {
}
