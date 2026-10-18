package com.hris.flow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hris.flow.entity.BizRelation;
import org.apache.ibatis.annotations.Mapper;

/**
 * 业务单据-流程映射数据访问。
 */
@Mapper
public interface BizRelationMapper extends BaseMapper<BizRelation> {
}
