package com.hris.compensation.dto;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;

/**
 * 算薪批次相关 DTO（域：薪酬，分层：DTO/接口契约）。算薪批次创建/更新/响应结构，含状态机字段。
 *
 * <p>复核字段（CMP-F-013b）：reviewedBy1/reviewedBy2 为两名不同复核人，两列齐全且互不相同
 * 才满足 REVIEW→APPROVE；响应回传复核时间与最近一次复核意见，供前端渲染双人复核进度。</p>
 */
public final class PayrollRunDtos {

    private PayrollRunDtos() {
    }

    /**
     * 创建请求。状态不接受入参：新建固定 INIT，提交走 submit 端点（防状态机旁路）。
     */
    public record PayrollRunCreateRequest(@NotNull(message = "periodCode 不能为空") String periodCode, @NotNull(message = "payGroupId 不能为空") Long payGroupId, Long calcBy, Long reviewBy) {
    }

    /**
     * 更新请求。状态不接受入参：仅可经 submit 端点或审批回调迁移（防状态机旁路）。
     */
    public record PayrollRunUpdateRequest(String periodCode, Long payGroupId, Long calcBy, Long reviewBy) {
    }

    /** 复核请求：approved=false 为驳回（退回计算，复核位清空）；comment 为意见/理由。 */
    public record PayrollReviewRequest(@NotNull(message = "approved 不能为空") Boolean approved, String comment) {
    }

    /** 响应。 */
    public record PayrollRunResponse(Long id, String periodCode, Long payGroupId, String status, Long calcBy, Long reviewBy, LocalDateTime createdAt, LocalDateTime updatedAt,
                                     Long reviewedBy1, LocalDateTime reviewedAt1, Long reviewedBy2, LocalDateTime reviewedAt2, String reviewComment) {
    }
}
