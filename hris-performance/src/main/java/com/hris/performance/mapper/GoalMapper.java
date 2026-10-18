package com.hris.performance.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hris.performance.entity.Goal;
import org.apache.ibatis.annotations.Mapper;

/**
 * prf_goal 数据访问。
 */
@Mapper
public interface GoalMapper extends BaseMapper<Goal> {
}
