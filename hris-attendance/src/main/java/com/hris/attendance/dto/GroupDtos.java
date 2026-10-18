package com.hris.attendance.dto;

import jakarta.validation.constraints.NotBlank;

import java.time.LocalDateTime;

/**
 * 考勤班组 DTO（ATT-F-001 考勤组与政策）。
 */
public final class GroupDtos {

    private GroupDtos() {
    }

    /** 班组创建/更新请求。name 必填，code 留空由服务端按前缀生成。 */
    public record GroupRequest(
            @NotBlank(message = "班组名称不能为空") String name,
            String code,
            Long leaderEmpId) {
    }

    /** 班组响应。 */
    public record GroupResponse(Long id, String name, String code, Long leaderEmpId,
                                LocalDateTime createdAt, LocalDateTime updatedAt) {
    }
}
