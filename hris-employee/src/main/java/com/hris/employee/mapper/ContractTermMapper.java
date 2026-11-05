package com.hris.employee.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hris.employee.entity.ContractTerm;
import org.apache.ibatis.annotations.Mapper;

/**
 * emp_contract_term 数据访问。
 */
@Mapper
public interface ContractTermMapper extends BaseMapper<ContractTerm> {
}
