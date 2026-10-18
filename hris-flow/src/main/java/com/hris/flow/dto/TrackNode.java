package com.hris.flow.dto;

/**
 * 流程轨迹节点（docs/04 §轨迹图数据：nodes+状态+耗时）。
 */
public record TrackNode(
        String nodeId,
        String nodeName,
        String type,
        String assignee,
        String status,
        String startTime,
        String endTime,
        long durationInMillis) {
}
