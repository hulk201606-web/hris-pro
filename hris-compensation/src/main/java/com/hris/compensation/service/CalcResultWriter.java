package com.hris.compensation.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hris.compensation.entity.CalcTask;
import com.hris.compensation.entity.PayItem;
import com.hris.compensation.entity.PayrollDetail;
import com.hris.compensation.mapper.CalcTaskMapper;
import com.hris.compensation.mapper.PayrollDetailMapper;
import com.hris.common.exception.BizException;
import com.hris.common.exception.ErrorCode;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 算薪结果落库（CMP-F-005：明细 + 任务行）。
 *
 * <p>明细口径：应发 = sum(EARNING)，实发 = NET 项结果；密文列照现有密域写法
 * 存金额明文（ADR-007 现行口径），掩码列固定 ****，密文不随通用响应暴露。
 * 任务行口径：cmp_calc_task 按 (run_id, emp_id) upsert，更新走 UpdateWrapper
 * 以支持成功重算时将 error 显式置 NULL。</p>
 */
@Component
public class CalcResultWriter {

    /** 仅用于序列化快照（writeValueAsString 线程安全）。 */
    private static final ObjectMapper JSON = new ObjectMapper();

    private final PayrollDetailMapper detailMapper;
    private final CalcTaskMapper calcTaskMapper;

    public CalcResultWriter(PayrollDetailMapper detailMapper, CalcTaskMapper calcTaskMapper) {
        this.detailMapper = detailMapper;
        this.calcTaskMapper = calcTaskMapper;
    }

    /** 应发 = sum(EARNING)，实发 = NET 项结果；input_snapshot 固化输入可复算。 */
    public void upsertDetail(com.hris.compensation.entity.PayrollRun run, Long empId, BigDecimal base,
                             List<PayItem> items, Map<String, Object> results) {
        BigDecimal amount = BigDecimal.ZERO;
        BigDecimal net = BigDecimal.ZERO;
        for (PayItem item : items) {
            Object v = results.get(item.getCode());
            if (v == null) {
                continue;
            }
            BigDecimal d = new BigDecimal(String.valueOf(v));
            switch (item.getCategory()) {
                case PayItem.CATEGORY_EARNING -> amount = amount.add(d);
                case PayItem.CATEGORY_NET -> net = d;
                default -> { /* 扣除/个税不进应发 */ }
            }
        }
        PayrollDetail detail = detailMapper.selectOne(new LambdaQueryWrapper<PayrollDetail>()
                .eq(PayrollDetail::getRunId, run.getId()).eq(PayrollDetail::getEmpId, empId));
        boolean create = detail == null;
        if (create) {
            detail = new PayrollDetail();
            detail.setRunId(run.getId());
            detail.setEmpId(empId);
        }
        detail.setInputSnapshot(writeJson(Map.of("periodCode", run.getPeriodCode(),
                "base", base.toPlainString(), "items", results)));
        detail.setAmountCipher(amount.toPlainString());
        detail.setAmountMask("****");
        detail.setNetAmountCipher(net.toPlainString());
        detail.setNetAmountMask("****");
        if (create) {
            detailMapper.insert(detail);
        } else if (detailMapper.updateById(detail) == 0) {
            throw new BizException(ErrorCode.CMP_ERROR, "算薪明细并发变更，请重试");
        }
    }

    /** 任务行 upsert；返回内存实体（含本次状态/原因/快照）供响应组装。 */
    public CalcTask upsertTask(Long runId, Long empId, String status, String error, String itemsJson) {
        CalcTask task = calcTaskMapper.selectOne(new LambdaQueryWrapper<CalcTask>()
                .eq(CalcTask::getRunId, runId).eq(CalcTask::getEmpId, empId));
        boolean create = task == null;
        if (create) {
            task = new CalcTask();
            task.setRunId(runId);
            task.setEmpId(empId);
        }
        task.setStatus(status);
        task.setError(error);
        if (itemsJson != null) {
            task.setItemsJson(itemsJson);
        }
        if (create) {
            calcTaskMapper.insert(task);
            return task;
        }
        // 更新走 UpdateWrapper：成功重试需将 error 显式置 NULL（updateById 默认忽略 null 字段）
        calcTaskMapper.update(null, new LambdaUpdateWrapper<CalcTask>()
                .eq(CalcTask::getId, task.getId())
                .set(CalcTask::getStatus, status)
                .set(CalcTask::getError, error)
                .set(itemsJson != null, CalcTask::getItemsJson, itemsJson)
                .set(CalcTask::getUpdatedAt, LocalDateTime.now()));
        return task;
    }

    private String writeJson(Object o) {
        try {
            return JSON.writeValueAsString(o);
        } catch (Exception e) {
            return "{}";
        }
    }
}
