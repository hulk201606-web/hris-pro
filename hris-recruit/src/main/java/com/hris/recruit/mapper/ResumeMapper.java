package com.hris.recruit.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hris.recruit.entity.Resume;
import org.apache.ibatis.annotations.Mapper;

/**
 * rcu_resume 数据访问。
 */
@Mapper
public interface ResumeMapper extends BaseMapper<Resume> {
}
