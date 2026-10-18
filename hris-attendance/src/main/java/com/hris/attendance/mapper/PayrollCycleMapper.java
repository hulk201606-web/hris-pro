package com.hris.attendance.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hris.attendance.entity.PayrollCycle;
import org.apache.ibatis.annotations.Mapper;

/**
 * 薪酬取数周期数据访问。
 */
@Mapper
public interface PayrollCycleMapper extends BaseMapper<PayrollCycle> {
}
