package com.hris.attendance.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hris.attendance.entity.AttDailyClose;
import org.apache.ibatis.annotations.Mapper;

/**
 * 日考勤月结锁数据访问（批11#19）。
 */
@Mapper
public interface AttDailyCloseMapper extends BaseMapper<AttDailyClose> {
}
