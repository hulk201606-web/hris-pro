package com.hris.compensation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

/**
 * 期间关账相关 DTO（CMP-F-020）。
 */
public final class PeriodLockDtos {

    private PeriodLockDtos() {
    }

    /** 解锁请求：理由必填且不少于 4 字（审计留痕）。 */
    public record PeriodUnlockRequest(
            @NotBlank(message = "reason 不能为空")
            @Size(min = 4, max = 255, message = "解锁理由至少 4 个字符") String reason) {
    }

    /** 期间锁响应。 */
    public record PeriodLockResponse(Long id, String periodCode, String status,
                                     Long lockedBy, LocalDateTime lockedAt,
                                     Long releasedBy, LocalDateTime releasedAt, String unlockReason) {
    }
}
