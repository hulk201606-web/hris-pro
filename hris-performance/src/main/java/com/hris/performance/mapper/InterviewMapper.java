package com.hris.performance.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hris.performance.entity.Interview;
import org.apache.ibatis.annotations.Mapper;

/**
 * prf_interview 数据访问。
 */
@Mapper
public interface InterviewMapper extends BaseMapper<Interview> {
}
