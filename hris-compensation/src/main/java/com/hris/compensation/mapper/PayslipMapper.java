package com.hris.compensation.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hris.compensation.entity.Payslip;
import org.apache.ibatis.annotations.Mapper;

/**
 * cmp_payslip 数据访问。
 */
@Mapper
public interface PayslipMapper extends BaseMapper<Payslip> {
}
