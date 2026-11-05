package com.hris.attendance.dto;

import jakarta.validation.constraints.NotBlank;

import java.time.LocalDateTime;

/**
 * 考勤组相关 DTO（域：考勤，分层：DTO/接口契约）。考勤组创建/更新/查询的请求与响应结构。
 */
public final class GroupDtos {

    private GroupDtos() {
    }

    /**
     * 班组创建/更新请求。name 必填，code 留空由服务端按前缀生成；
     * holidayCalendarYear 绑定节假日历年度（空 = 不绑定）。
     */
    public record GroupRequest(
            @NotBlank(message = "班组名称不能为空") String name,
            String code,
            Long leaderEmpId,
            Integer holidayCalendarYear) {
    }

    /** 班组响应。 */
    public record GroupResponse(Long id, String name, String code, Long leaderEmpId,
                                Integer holidayCalendarYear,
                                LocalDateTime createdAt, LocalDateTime updatedAt) {
    }

    /**
     * 组成员批量设置请求（批6#10，全量覆盖语义）：empIds 去重后即为该组最终成员，
     * 空列表表示清空组成员（节假日判定随之降级到租户并集）。
     */
    public record GroupMemberSetRequest(java.util.List<Long> empIds) {
    }
}
