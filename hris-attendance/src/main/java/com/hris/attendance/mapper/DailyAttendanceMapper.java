package com.hris.attendance.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hris.attendance.entity.DailyAttendance;
import org.apache.ibatis.annotations.Mapper;

/**
 * 日考勤结果数据访问。
 */
@Mapper
public interface DailyAttendanceMapper extends BaseMapper<DailyAttendance> {
}
