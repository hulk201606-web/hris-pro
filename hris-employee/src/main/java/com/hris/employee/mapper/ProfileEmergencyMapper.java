package com.hris.employee.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hris.employee.entity.ProfileEmergency;
import org.apache.ibatis.annotations.Mapper;

/**
 * 紧急联系人数据访问。
 */
@Mapper
public interface ProfileEmergencyMapper extends BaseMapper<ProfileEmergency> {
}
