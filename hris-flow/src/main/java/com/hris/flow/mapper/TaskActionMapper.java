package com.hris.flow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hris.flow.entity.TaskAction;
import org.apache.ibatis.annotations.Mapper;

/**
 * 审批动作明细数据访问。
 */
@Mapper
public interface TaskActionMapper extends BaseMapper<TaskAction> {
}
