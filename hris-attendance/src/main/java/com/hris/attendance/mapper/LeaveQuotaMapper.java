package com.hris.attendance.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hris.attendance.entity.LeaveQuota;
import org.apache.ibatis.annotations.Mapper;

/**
 * 假期额度数据访问。
 */
@Mapper
public interface LeaveQuotaMapper extends BaseMapper<LeaveQuota> {
}
