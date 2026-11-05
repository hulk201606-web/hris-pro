package com.hris.flow.dto;

import jakarta.validation.constraints.NotNull;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 审批委托代理 DTO（FLW-F-009）。
 *
 * <p>userId 语义：fromUser/toUser 均为网关透传 userId（X-User-Id，与 TraceContext 取值一致）。</p>
 */
public final class DelegateDtos {

    private DelegateDtos() {
    }

    /** 创建委托请求（procDefKey 缺省 NULL=全部流程；fromUser 缺省=当前登录用户） */
    public record CreateRequest(
            Long fromUser,
            @NotNull(message = "toUser 不能为空") Long toUser,
            String procDefKey,
            @NotNull(message = "startAt 不能为空")
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startAt,
            @NotNull(message = "endAt 不能为空")
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endAt,
            String reason) {
    }

    /** 委托记录响应 */
    public record DelegateResponse(
            Long id,
            Long fromUser,
            Long toUser,
            String procDefKey,
            LocalDateTime startAt,
            LocalDateTime endAt,
            String status,
            String reason) {
    }

    /** 我的委托：我创建的（created）+ 委托给我的（received） */
    public record MineResponse(List<DelegateResponse> created, List<DelegateResponse> received) {
    }
}
