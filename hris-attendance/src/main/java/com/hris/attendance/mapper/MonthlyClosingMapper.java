package com.hris.attendance.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hris.attendance.entity.MonthlyClosing;
import org.apache.ibatis.annotations.Mapper;

/**
 * 月结批次数据访问。
 */
@Mapper
public interface MonthlyClosingMapper extends BaseMapper<MonthlyClosing> {
}
