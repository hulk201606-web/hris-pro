package com.hris.attendance.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hris.attendance.entity.ShiftAssignment;
import org.apache.ibatis.annotations.Mapper;

/**
 * 排班数据访问。
 */
@Mapper
public interface ShiftAssignmentMapper extends BaseMapper<ShiftAssignment> {
}
