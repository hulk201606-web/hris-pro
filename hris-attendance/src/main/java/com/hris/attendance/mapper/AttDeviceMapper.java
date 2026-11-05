package com.hris.attendance.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hris.attendance.entity.AttDevice;
import org.apache.ibatis.annotations.Mapper;

/**
 * 考勤机设备数据访问（批12#27）。
 */
@Mapper
public interface AttDeviceMapper extends BaseMapper<AttDevice> {
}
