package com.hris.employee.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hris.employee.entity.LifecycleCase;
import org.apache.ibatis.annotations.Mapper;

/**
 * 生命周期单数据访问。
 */
@Mapper
public interface LifecycleCaseMapper extends BaseMapper<LifecycleCase> {
}
