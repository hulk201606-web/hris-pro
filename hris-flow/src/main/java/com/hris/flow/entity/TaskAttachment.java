package com.hris.flow.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.hris.common.entity.BaseEntity;

/**
 * 任务节点附件（flow_ext_task_attachment，FLW-F-020）。
 *
 * <p>文件本体在 PLT 文件服务（{@code sys_file_meta}），本表只存引用与归属；
 * 唯一约束 uk(tenant_id, task_id, file_id) 保证同一任务不重复挂同一文件。</p>
 */
@TableName("flow_ext_task_attachment")
public class TaskAttachment extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 流程实例 ID（逻辑外键 ACT_RU_TASK.PROC_INST_ID_） */
    private String instanceId;

    /** 任务 ID（逻辑外键 ACT_RU_TASK.ID_） */
    private String taskId;

    /** PLT 文件 ID（sys_file_meta.id 或对象键，VARCHAR 兼容两种形态） */
    private String fileId;

    /** 文件名（上传时快照，便于列表展示无需回查 PLT） */
    private String fileName;

    /** 上传人用户 ID（逻辑外键 auth_user.id） */
    private Long uploadedBy;

    public String getInstanceId() {
        return instanceId;
    }

    public void setInstanceId(String instanceId) {
        this.instanceId = instanceId;
    }

    public String getTaskId() {
        return taskId;
    }

    public void setTaskId(String taskId) {
        this.taskId = taskId;
    }

    public String getFileId() {
        return fileId;
    }

    public void setFileId(String fileId) {
        this.fileId = fileId;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public Long getUploadedBy() {
        return uploadedBy;
    }

    public void setUploadedBy(Long uploadedBy) {
        this.uploadedBy = uploadedBy;
    }
}
