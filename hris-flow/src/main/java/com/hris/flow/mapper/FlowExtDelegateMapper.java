package com.hris.flow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hris.flow.entity.FlowExtDelegate;
import org.apache.ibatis.annotations.Mapper;

/**
 * 审批委托代理数据访问（FLW-F-009）。
 */
@Mapper
public interface FlowExtDelegateMapper extends BaseMapper<FlowExtDelegate> {
}
