package com.hris.performance.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 绩效目标相关 DTO（域：绩效，分层：DTO/接口契约）。目标设定/更新/批量提交/结果录入的请求与响应结构。
 *
 * <p>权重口径（PRF-F-003）：同周期 + 同员工 + 同维度（dimension）权重合计 = 100。
 * 单条录入阶段只拦"合计超 100"，整体提交（batch）要求恰好 100。</p>
 */
public final class GoalDtos {

    private GoalDtos() {
    }

    /** 创建请求。dimension 省略时按 KPI 处理。 */
    public record GoalCreateRequest(@NotNull(message = "cycleId 不能为空") Long cycleId, @NotNull(message = "empId 不能为空") Long empId, @NotNull(message = "title 不能为空") String title, String dimension, @NotNull(message = "weight 不能为空") BigDecimal weight, String targetValue, String resultValue) {
    }

    /** 更新请求。 */
    public record GoalUpdateRequest(Long cycleId, Long empId, String title, String dimension, BigDecimal weight, String targetValue, String resultValue) {
    }

    /** 批量提交单条目标（无 ID，整体替换语义）。 */
    public record GoalBatchItem(@NotNull(message = "title 不能为空") String title, @NotNull(message = "weight 不能为空") BigDecimal weight, String targetValue, String resultValue) {
    }

    /**
     * 批量提交请求（bizKey = cycleId:empId:dimension 整体提交）：
     * 先对 items 聚合校验权重合计，通过后整体替换该维度下的目标集合。
     */
    public record GoalBatchSaveRequest(@NotNull(message = "cycleId 不能为空") Long cycleId, @NotNull(message = "empId 不能为空") Long empId, String dimension, @NotEmpty(message = "items 不能为空") @Valid List<GoalBatchItem> items) {
    }

    /** 响应。 */
    public record GoalResponse(Long id, Long cycleId, Long empId, String title, String dimension, BigDecimal weight, String targetValue, String resultValue, LocalDateTime createdAt, LocalDateTime updatedAt) {
    }
}
