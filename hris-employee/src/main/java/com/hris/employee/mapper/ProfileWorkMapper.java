package com.hris.employee.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hris.employee.entity.ProfileWork;
import org.apache.ibatis.annotations.Mapper;

/**
 * 工作经历数据访问。
 */
@Mapper
public interface ProfileWorkMapper extends BaseMapper<ProfileWork> {
}
