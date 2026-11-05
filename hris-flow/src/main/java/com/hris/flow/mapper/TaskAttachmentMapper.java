package com.hris.flow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hris.flow.entity.TaskAttachment;
import org.apache.ibatis.annotations.Mapper;

/**
 * 任务节点附件数据访问（flow_ext_task_attachment，FLW-F-020）。
 */
@Mapper
public interface TaskAttachmentMapper extends BaseMapper<TaskAttachment> {
}
