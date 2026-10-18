package com.hris.flow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hris.flow.entity.FlowModel;
import org.apache.ibatis.annotations.Mapper;

/**
 * 流程模型数据访问。
 */
@Mapper
public interface FlowModelMapper extends BaseMapper<FlowModel> {
}
