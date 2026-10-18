package com.hris.performance.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hris.performance.entity.Enrollment;
import org.apache.ibatis.annotations.Mapper;

/**
 * prf_enrollment 数据访问。
 */
@Mapper
public interface EnrollmentMapper extends BaseMapper<Enrollment> {
}
