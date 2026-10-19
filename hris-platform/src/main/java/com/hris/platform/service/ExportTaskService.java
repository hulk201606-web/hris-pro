package com.hris.platform.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hris.common.exception.BizException;
import com.hris.common.exception.ErrorCode;
import com.hris.platform.dto.PageResult;
import com.hris.platform.dto.ReportDtos.ExportTaskRequest;
import com.hris.platform.dto.ReportDtos.ExportTaskResponse;
import com.hris.platform.entity.SysExportTask;
import com.hris.platform.mapper.SysExportTaskMapper;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 异步导出任务（PLT-F-005：PENDING/RUNNING/DONE/FAILED 状态机，导出执行器由调度任务接管）。
 */
@Service
public class ExportTaskService {

    private final SysExportTaskMapper mapper;

    public ExportTaskService(SysExportTaskMapper mapper) {
        this.mapper = mapper;
    }

    /** 创建异步导出任务（写入 outbox 由消费者执行）。 */
    public ExportTaskResponse create(ExportTaskRequest req) {
        SysExportTask t = new SysExportTask();
        t.setReportDefId(req.reportDefId());
        t.setStatus("PENDING");
        mapper.insert(t);
        return toResponse(t);
    }

    /** 按状态分页查询导出任务。 */
    public PageResult<ExportTaskResponse> query(String status, long page, long size) {
        LambdaQueryWrapper<SysExportTask> qw = new LambdaQueryWrapper<>();
        if (status != null && !status.isBlank()) {
            qw.eq(SysExportTask::getStatus, status.trim().toUpperCase());
        }
        qw.orderByDesc(SysExportTask::getCreatedAt);
        Page<SysExportTask> p = mapper.selectPage(new Page<>(page, size), qw);
        List<ExportTaskResponse> records = p.getRecords().stream().map(this::toResponse).toList();
        return new PageResult<>(records, p.getCurrent(), p.getSize(), p.getTotal());
    }

    /** 导出执行器回调：完成并关联文件。 */
    public void markDone(Long id, Long fileId) {
        SysExportTask t = require(id);
        t.setStatus("DONE");
        t.setFileId(fileId);
        mapper.updateById(t);
    }

    /** 导出执行器回调：失败。 */
    public void markFailed(Long id) {
        SysExportTask t = require(id);
        t.setStatus("FAILED");
        mapper.updateById(t);
    }

    private SysExportTask require(Long id) {
        SysExportTask t = mapper.selectById(id);
        if (t == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "导出任务不存在");
        }
        return t;
    }

    private ExportTaskResponse toResponse(SysExportTask t) {
        return new ExportTaskResponse(t.getId(), t.getReportDefId(), t.getStatus(), t.getFileId(),
                t.getCreatedAt());
    }
}
