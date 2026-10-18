package com.hris.employee.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hris.employee.entity.Employee;
import org.apache.ibatis.annotations.Mapper;

/**
 * 员工主档数据访问。
 */
@Mapper
public interface EmployeeMapper extends BaseMapper<Employee> {
}
