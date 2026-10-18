package com.hris.employee.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hris.employee.entity.StatusLog;
import org.apache.ibatis.annotations.Mapper;

/**
 * 员工状态流水数据访问。
 */
@Mapper
public interface StatusLogMapper extends BaseMapper<StatusLog> {
}
