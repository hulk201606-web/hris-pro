package com.hris.attendance.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hris.attendance.entity.LeaveRequest;
import org.apache.ibatis.annotations.Mapper;

/**
 * 请假单数据访问。
 */
@Mapper
public interface LeaveRequestMapper extends BaseMapper<LeaveRequest> {
}
