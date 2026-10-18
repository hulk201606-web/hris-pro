package com.hris.attendance.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hris.attendance.entity.PunchRecord;
import org.apache.ibatis.annotations.Mapper;

/**
 * 打卡记录数据访问。
 */
@Mapper
public interface PunchRecordMapper extends BaseMapper<PunchRecord> {
}
