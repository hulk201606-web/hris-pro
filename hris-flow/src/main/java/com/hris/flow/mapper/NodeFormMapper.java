package com.hris.flow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hris.flow.entity.NodeForm;
import org.apache.ibatis.annotations.Mapper;

/**
 * 节点表单绑定数据访问。
 */
@Mapper
public interface NodeFormMapper extends BaseMapper<NodeForm> {
}
