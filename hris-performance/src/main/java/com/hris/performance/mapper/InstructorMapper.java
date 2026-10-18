package com.hris.performance.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hris.performance.entity.Instructor;
import org.apache.ibatis.annotations.Mapper;

/**
 * prf_instructor 数据访问。
 */
@Mapper
public interface InstructorMapper extends BaseMapper<Instructor> {
}
