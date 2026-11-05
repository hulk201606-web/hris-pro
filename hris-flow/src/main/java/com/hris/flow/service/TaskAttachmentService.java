package com.hris.flow.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hris.common.exception.BizException;
import com.hris.common.exception.ErrorCode;
import com.hris.common.trace.TraceContext;
import com.hris.flow.dto.TaskExtDtos.AddAttachmentRequest;
import com.hris.flow.dto.TaskExtDtos.AttachmentView;
import com.hris.flow.entity.TaskAttachment;
import com.hris.flow.mapper.TaskAttachmentMapper;
import org.flowable.task.api.Task;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 节点级附件服务（FLW-F-020）。
 *
 * <p>归属规则：仅任务办理人（assignee / 候选人 / 委托代办）可上传，且任务须处于运行中；
 * 任务完成后任务查询为空，自然拒绝（30404）。文件本体在 PLT 文件服务，本服务只维护引用。</p>
 *
 * <p>幂等：同一任务重复挂同一 fileId 返回既有记录，不产生重复行（配合 uk 兜底并发）。</p>
 */
@Service
public class TaskAttachmentService {

    private static final Logger log = LoggerFactory.getLogger(TaskAttachmentService.class);

    private static final int MAX_FILE_ID_LEN = 128;
    private static final int MAX_FILE_NAME_LEN = 255;

    private final TaskAttachmentMapper attachmentMapper;
    private final TaskGuard taskGuard;

    public TaskAttachmentService(TaskAttachmentMapper attachmentMapper, TaskGuard taskGuard) {
        this.attachmentMapper = attachmentMapper;
        this.taskGuard = taskGuard;
    }

    /** 上传登记：校验办理人 → 落引用；重复 fileId 幂等返回。 */
    @Transactional(rollbackFor = Exception.class)
    public AttachmentView add(String taskId, AddAttachmentRequest req) {
        Task task = taskGuard.requireWritableTask(taskId);
        String fileId = requireText(req == null ? null : req.fileId(), MAX_FILE_ID_LEN, "fileId");
        String fileName = requireText(req == null ? null : req.fileName(), MAX_FILE_NAME_LEN, "fileName");

        TaskAttachment existing = find(taskId, fileId);
        if (existing != null) {
            return toView(existing);
        }
        TaskAttachment row = new TaskAttachment();
        row.setInstanceId(task.getProcessInstanceId());
        row.setTaskId(taskId);
        row.setFileId(fileId);
        row.setFileName(fileName);
        row.setUploadedBy(TraceContext.getUserId());
        try {
            attachmentMapper.insert(row);
        } catch (DuplicateKeyException e) {
            log.debug("附件并发重复，返回既有记录: taskId={}, fileId={}", taskId, fileId);
            TaskAttachment dup = find(taskId, fileId);
            return dup == null ? toView(row) : toView(dup);
        }
        return toView(row);
    }

    /** 任务附件列表（按上传时间正序）；租户隔离由 MyBatis-Plus 租户插件保证。 */
    public List<AttachmentView> list(String taskId) {
        if (taskId == null || taskId.isBlank()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "taskId 不能为空");
        }
        return attachmentMapper.selectList(new LambdaQueryWrapper<TaskAttachment>()
                        .eq(TaskAttachment::getTaskId, taskId)
                        .orderByAsc(TaskAttachment::getCreatedAt))
                .stream().map(TaskAttachmentService::toView).toList();
    }

    private TaskAttachment find(String taskId, String fileId) {
        return attachmentMapper.selectOne(new LambdaQueryWrapper<TaskAttachment>()
                .eq(TaskAttachment::getTaskId, taskId)
                .eq(TaskAttachment::getFileId, fileId)
                .last("limit 1"));
    }

    private static AttachmentView toView(TaskAttachment row) {
        return new AttachmentView(row.getId(), row.getInstanceId(), row.getTaskId(), row.getFileId(),
                row.getFileName(), row.getUploadedBy(), row.getCreatedAt());
    }

    private static String requireText(String value, int maxLen, String field) {
        if (value == null || value.isBlank()) {
            throw new BizException(ErrorCode.PARAM_INVALID, field + " 不能为空");
        }
        String v = value.trim();
        if (v.length() > maxLen) {
            throw new BizException(ErrorCode.PARAM_INVALID, field + " 超长（上限 " + maxLen + "）");
        }
        return v;
    }
}
