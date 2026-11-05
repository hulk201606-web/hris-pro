package com.hris.compensation.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hris.compensation.dto.CalcTaskDtos.CalcProgressResponse;
import com.hris.compensation.dto.CalcTaskDtos.CalcTaskResponse;
import com.hris.compensation.entity.CalcTask;
import com.hris.compensation.mapper.CalcTaskMapper;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 算薪任务监控查询服务（CMP-F-006）：逐员工状态 + 失败定位 + 进度汇总。
 * 读取口径与 PayrollCalcService 的任务行写入口径一致（status 过滤走索引 idx_cmp_calc_task_run）。
 */
@Service
public class PayrollCalcTaskService {

    private final CalcTaskMapper calcTaskMapper;
    private final PayrollRunService runService;

    public PayrollCalcTaskService(CalcTaskMapper calcTaskMapper, PayrollRunService runService) {
        this.calcTaskMapper = calcTaskMapper;
        this.runService = runService;
    }

    /** 逐员工任务列表（可按 status 过滤：SUCCESS/FAILED/SKIPPED/RUNNING）。 */
    public List<CalcTaskResponse> tasks(Long runId, String status) {
        requireRun(runId);
        List<CalcTask> tasks = calcTaskMapper.selectList(new LambdaQueryWrapper<CalcTask>()
                .eq(CalcTask::getRunId, runId)
                .eq(status != null && !status.isBlank(), CalcTask::getStatus, status)
                .orderByAsc(CalcTask::getEmpId));
        return tasks.stream().map(this::toResponse).toList();
    }

    /** 计算进度：总数/成功/失败/跳过/进度百分比（成功+失败+跳过 占总数）。 */
    public CalcProgressResponse progress(Long runId) {
        requireRun(runId);
        List<CalcTask> tasks = calcTaskMapper.selectList(new LambdaQueryWrapper<CalcTask>()
                .eq(CalcTask::getRunId, runId));
        long success = tasks.stream().filter(t -> isStatus(t, PayrollCalcService.TASK_SUCCESS)).count();
        long failed = tasks.stream().filter(t -> isStatus(t, PayrollCalcService.TASK_FAILED)).count();
        long skipped = tasks.stream().filter(t -> isStatus(t, PayrollCalcService.TASK_SKIPPED)).count();
        long total = tasks.size();
        int percent = total == 0 ? 0 : (int) Math.round((success + failed + skipped) * 100.0 / total);
        return new CalcProgressResponse(runId, total, success, failed, skipped, percent);
    }

    private boolean isStatus(CalcTask t, String status) {
        return status.equals(t.getStatus());
    }

    private void requireRun(Long runId) {
        // 不存在时 get() 抛 NOT_FOUND
        runService.get(runId);
    }

    private CalcTaskResponse toResponse(CalcTask t) {
        return new CalcTaskResponse(t.getId(), t.getRunId(), t.getEmpId(), t.getStatus(),
                t.getError(), t.getItemsJson(), t.getCreatedAt(), t.getUpdatedAt());
    }
}
