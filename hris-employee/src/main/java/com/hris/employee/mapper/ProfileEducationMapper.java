package com.hris.employee.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hris.employee.entity.ProfileEducation;
import org.apache.ibatis.annotations.Mapper;

/**
 * 教育经历数据访问。
 */
@Mapper
public interface ProfileEducationMapper extends BaseMapper<ProfileEducation> {
}
