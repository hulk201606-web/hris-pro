package com.hris.flow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hris.flow.entity.FlowCc;
import org.apache.ibatis.annotations.Mapper;

/**
 * 审批抄送记录数据访问。
 */
@Mapper
public interface FlowCcMapper extends BaseMapper<FlowCc> {
}
