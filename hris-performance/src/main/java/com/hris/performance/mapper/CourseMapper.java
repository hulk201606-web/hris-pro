package com.hris.performance.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hris.performance.entity.Course;
import org.apache.ibatis.annotations.Mapper;

/**
 * prf_course 数据访问。
 */
@Mapper
public interface CourseMapper extends BaseMapper<Course> {
}
