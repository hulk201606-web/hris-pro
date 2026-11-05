package com.hris.performance.dto;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 绩效面谈相关 DTO（域：绩效，分层：DTO/接口契约）。
 * 创建/更新请求中 pip_status 不可传入——状态机由服务端维护（conclusion=PIP 缺省
 * ONGOING，迁移只经 POST /{id}/pip-result）。
 */
public final class InterviewDtos {

    private InterviewDtos() {
    }

    /** 创建请求。 */
    public record InterviewCreateRequest(
            @NotNull(message = "cycleId 不能为空") Long cycleId,
            @NotNull(message = "empId 不能为空") Long empId,
            Integer round,
            @NotNull(message = "interviewDate 不能为空") LocalDate interviewDate,
            @NotNull(message = "interviewerEmpId 不能为空") Long interviewerEmpId,
            String summary,
            @NotNull(message = "conclusion 不能为空") String conclusion,
            LocalDate pipStartDate,
            LocalDate pipEndDate) {
    }

    /** 更新请求（pip_status 旁路封死，只经 pip-result 迁移）。 */
    public record InterviewUpdateRequest(
            Long cycleId,
            Long empId,
            Integer round,
            LocalDate interviewDate,
            Long interviewerEmpId,
            String summary,
            String conclusion,
            LocalDate pipStartDate,
            LocalDate pipEndDate) {
    }

    /** PIP 结果登记请求（仅 PASSED/FAILED）。 */
    public record PipResultRequest(@NotNull(message = "pipStatus 不能为空") String pipStatus) {
    }

    /** 响应。 */
    public record InterviewResponse(
            Long id, Long cycleId, Long empId, Integer round, LocalDate interviewDate,
            Long interviewerEmpId, String summary, String conclusion,
            LocalDate pipStartDate, LocalDate pipEndDate, String pipStatus,
            LocalDateTime createdAt, LocalDateTime updatedAt) {
    }
}
