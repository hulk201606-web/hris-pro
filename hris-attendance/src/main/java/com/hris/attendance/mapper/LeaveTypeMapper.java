package com.hris.attendance.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hris.attendance.entity.LeaveType;
import org.apache.ibatis.annotations.Mapper;

/**
 * 假种数据访问。
 */
@Mapper
public interface LeaveTypeMapper extends BaseMapper<LeaveType> {
}
