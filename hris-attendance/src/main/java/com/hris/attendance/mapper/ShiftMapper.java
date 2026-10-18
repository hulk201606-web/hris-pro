package com.hris.attendance.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hris.attendance.entity.Shift;
import org.apache.ibatis.annotations.Mapper;

/**
 * 班次数据访问。
 */
@Mapper
public interface ShiftMapper extends BaseMapper<Shift> {
}
