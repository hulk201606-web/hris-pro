package com.hris.flow.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hris.common.exception.BizException;
import com.hris.common.exception.ErrorCode;
import com.hris.common.trace.TraceContext;
import com.hris.flow.dto.TaskExtDtos.OpinionTagRequest;
import com.hris.flow.dto.TaskExtDtos.OpinionTagView;
import com.hris.flow.entity.TaskOpinionTag;
import com.hris.flow.enums.OpinionTagType;
import com.hris.flow.mapper.TaskOpinionTagMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 结构化审批意见标签服务（FLW-F-020）。
 *
 * <p>两个入口：</p>
 * <ul>
 *   <li>{@link #tag(String, OpinionTagRequest)}：独立打标端点，需办理人且任务未完成；</li>
 *   <li>{@link #recordForAction(String, String, String)}：审批动作（approve/reject）内联提交
 *       comment + tag，任务已由动作服务校验，这里只做枚举与长度校验。</li>
 * </ul>
 */
@Service
public class TaskOpinionService {

    private static final int MAX_COMMENT_LEN = 512;

    private final TaskOpinionTagMapper opinionTagMapper;
    private final TaskGuard taskGuard;

    public TaskOpinionService(TaskOpinionTagMapper opinionTagMapper, TaskGuard taskGuard) {
        this.opinionTagMapper = opinionTagMapper;
        this.taskGuard = taskGuard;
    }

    /** 独立打标：仅办理人、任务运行中。 */
    @Transactional(rollbackFor = Exception.class)
    public OpinionTagView tag(String taskId, OpinionTagRequest req) {
        taskGuard.requireWritableTask(taskId);
        return insert(taskId, req == null ? null : req.tag(), req == null ? null : req.comment());
    }

    /** 审批动作内联打标：tag 为空时静默跳过（保持既有审批调用零改动）。 */
    @Transactional(rollbackFor = Exception.class)
    public void recordForAction(String taskId, String tag, String comment) {
        if (tag == null || tag.isBlank()) {
            return;
        }
        insert(taskId, tag, comment);
    }

    /** 任务标签列表（按打标时间正序）。 */
    public List<OpinionTagView> list(String taskId) {
        if (taskId == null || taskId.isBlank()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "taskId 不能为空");
        }
        return opinionTagMapper.selectList(new LambdaQueryWrapper<TaskOpinionTag>()
                        .eq(TaskOpinionTag::getTaskId, taskId)
                        .orderByAsc(TaskOpinionTag::getCreatedAt))
                .stream().map(TaskOpinionService::toView).toList();
    }

    private OpinionTagView insert(String taskId, String rawTag, String rawComment) {
        OpinionTagType tag = OpinionTagType.require(rawTag);
        String comment = normalizeComment(rawComment);
        TaskOpinionTag row = new TaskOpinionTag();
        row.setTaskId(taskId);
        row.setTag(tag.name());
        row.setComment(comment);
        row.setOperator(TraceContext.getUserId());
        opinionTagMapper.insert(row);
        return toView(row);
    }

    private static String normalizeComment(String comment) {
        if (comment == null) {
            return null;
        }
        String c = comment.trim();
        if (c.length() > MAX_COMMENT_LEN) {
            throw new BizException(ErrorCode.PARAM_INVALID, "comment 超长（上限 " + MAX_COMMENT_LEN + "）");
        }
        return c.isEmpty() ? null : c;
    }

    private static OpinionTagView toView(TaskOpinionTag row) {
        return new OpinionTagView(row.getId(), row.getTaskId(), row.getTag(), row.getComment(),
                row.getOperator(), row.getCreatedAt());
    }
}
