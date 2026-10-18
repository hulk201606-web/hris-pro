package com.hris.platform.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.hris.common.entity.BaseEntity;

/**
 * 异步导出任务（PLT-F-005：状态机 PENDING/RUNNING/DONE/FAILED）。
 */
@TableName("sys_export_task")
public class SysExportTask extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 报表定义 ID（逻辑外键 sys_report_def.id） */
    private Long reportDefId;

    /** 状态：PENDING/RUNNING/DONE/FAILED */
    private String status;

    /** 导出文件 ID（逻辑外键 sys_file_meta.id） */
    private Long fileId;

    public Long getReportDefId() {
        return reportDefId;
    }

    public void setReportDefId(Long reportDefId) {
        this.reportDefId = reportDefId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Long getFileId() {
        return fileId;
    }

    public void setFileId(Long fileId) {
        this.fileId = fileId;
    }
}
