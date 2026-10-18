package com.hris.attendance.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hris.attendance.entity.AttendanceException;
import org.apache.ibatis.annotations.Mapper;

/**
 * 考勤异常数据访问。
 */
@Mapper
public interface AttendanceExceptionMapper extends BaseMapper<AttendanceException> {
}
