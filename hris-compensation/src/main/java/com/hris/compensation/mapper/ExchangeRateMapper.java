package com.hris.compensation.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hris.compensation.entity.ExchangeRate;
import org.apache.ibatis.annotations.Mapper;

/**
 * 汇率数据访问（CMP-F-023）。
 */
@Mapper
public interface ExchangeRateMapper extends BaseMapper<ExchangeRate> {
}
