package com.hris.attendance.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hris.attendance.entity.AttLocation;
import org.apache.ibatis.annotations.Mapper;

/**
 * 考勤围栏数据访问（批12#27）。
 */
@Mapper
public interface AttLocationMapper extends BaseMapper<AttLocation> {
}
