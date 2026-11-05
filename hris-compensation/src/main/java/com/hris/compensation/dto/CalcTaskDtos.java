package com.hris.compensation.dto;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 算薪引擎/任务监控相关 DTO（CMP-F-005/006/021）。
 */
public final class CalcTaskDtos {

    private CalcTaskDtos() {
    }

    /** 算薪触发响应（同步 MVP，返回批次状态与任务汇总）。 */
    public record CalcRunResponse(Long runId, String status, long total, long success,
                                  long failed, long skipped) {
    }

    /** 逐员工任务行响应。 */
    public record CalcTaskResponse(Long id, Long runId, Long empId, String status, String error,
                                   String itemsJson, LocalDateTime createdAt, LocalDateTime updatedAt) {
    }

    /** 计算进度响应（总数/成功/失败/跳过/进度百分比）。 */
    public record CalcProgressResponse(Long runId, long total, long success, long failed,
                                       long skipped, int progressPercent) {
    }

    /** 公式校验请求（F-021；sampleVars 可空，缺省给演示值）。 */
    public record FormulaValidateRequest(String formula, Map<String, Object> sampleVars) {
    }

    /** 公式校验响应：结果 + 变量/函数白名单清单。 */
    public record FormulaValidateResponse(boolean valid, String message, String result,
                                          List<String> variables, List<String> functions,
                                          List<String> baseVariables) {
    }
}
