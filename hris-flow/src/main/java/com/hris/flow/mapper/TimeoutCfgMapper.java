package com.hris.flow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hris.flow.entity.TimeoutCfg;
import org.apache.ibatis.annotations.Mapper;

/**
 * 节点超时配置数据访问。
 */
@Mapper
public interface TimeoutCfgMapper extends BaseMapper<TimeoutCfg> {
}
