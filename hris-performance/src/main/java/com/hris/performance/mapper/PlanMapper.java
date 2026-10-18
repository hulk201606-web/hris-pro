package com.hris.performance.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hris.performance.entity.Plan;
import org.apache.ibatis.annotations.Mapper;

/**
 * prf_plan 数据访问。
 */
@Mapper
public interface PlanMapper extends BaseMapper<Plan> {
}
