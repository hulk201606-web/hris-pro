package com.hris.employee.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hris.employee.entity.ProfileFamily;
import org.apache.ibatis.annotations.Mapper;

/**
 * 家庭成员数据访问。
 */
@Mapper
public interface ProfileFamilyMapper extends BaseMapper<ProfileFamily> {
}
