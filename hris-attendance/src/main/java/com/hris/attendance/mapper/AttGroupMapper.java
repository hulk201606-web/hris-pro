package com.hris.attendance.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hris.attendance.entity.AttGroup;
import org.apache.ibatis.annotations.Mapper;

/**
 * 考勤班组数据访问。
 */
@Mapper
public interface AttGroupMapper extends BaseMapper<AttGroup> {
}
