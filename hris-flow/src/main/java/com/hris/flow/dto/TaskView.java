package com.hris.flow.dto;

/**
 * 待办/已办/我发起列表项（跨流程聚合，FLW-F-006/011）。
 */
public record TaskView(
        String taskId,
        String instanceId,
        String procDefKey,
        String bizType,
        String bizKey,
        String nodeName,
        String assignee,
        String status,
        String startTime,
        String createTime,
        String endTime,
        long durationInMillis) {
}
