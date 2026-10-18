package com.hris.employee.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hris.employee.entity.Employment;
import org.apache.ibatis.annotations.Mapper;

/**
 * 雇佣关系数据访问。
 */
@Mapper
public interface EmploymentMapper extends BaseMapper<Employment> {
}
