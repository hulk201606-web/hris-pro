package com.hris.flow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hris.flow.entity.TaskOpinionTag;
import org.apache.ibatis.annotations.Mapper;

/**
 * 任务结构化意见标签数据访问（flow_ext_task_opinion_tag，FLW-F-020）。
 */
@Mapper
public interface TaskOpinionTagMapper extends BaseMapper<TaskOpinionTag> {
}
