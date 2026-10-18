package com.hris.recruit.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hris.recruit.entity.Interview;
import org.apache.ibatis.annotations.Mapper;

/**
 * rcu_interview 数据访问。
 */
@Mapper
public interface InterviewMapper extends BaseMapper<Interview> {
}
