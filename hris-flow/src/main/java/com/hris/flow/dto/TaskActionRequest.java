package com.hris.flow.dto;

import java.util.List;

/**
 * 任务操作请求（同意/驳回/转办/加签，FLW-F-007）。
 * 撤回无请求体（发起人身份由当前登录用户判定）。
 */
public record TaskActionRequest(
        /** 审批意见 */
        String comment,
        /** 附件列表（fileId 引用） */
        List<Long> attachments,
        /** 转办/加签目标用户 ID */
        Long targetUserId) {
}
