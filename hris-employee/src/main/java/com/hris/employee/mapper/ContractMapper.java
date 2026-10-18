package com.hris.employee.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hris.employee.entity.Contract;
import org.apache.ibatis.annotations.Mapper;

/**
 * 合同台账数据访问。
 */
@Mapper
public interface ContractMapper extends BaseMapper<Contract> {
}
