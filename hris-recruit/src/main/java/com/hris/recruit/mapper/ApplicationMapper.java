package com.hris.recruit.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hris.recruit.entity.Application;
import org.apache.ibatis.annotations.Mapper;

/**
 * rcu_application 数据访问。
 */
@Mapper
public interface ApplicationMapper extends BaseMapper<Application> {
}
