package com.hris.recruit.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hris.recruit.entity.Candidate;
import org.apache.ibatis.annotations.Mapper;

/**
 * rcu_candidate 数据访问。
 */
@Mapper
public interface CandidateMapper extends BaseMapper<Candidate> {
}
