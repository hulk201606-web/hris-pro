package com.hris.flow.dto;

import java.util.List;

/**
 * 流程轨迹图数据（FLW-F-011）。
 */
public record TrackResponse(
        String instanceId,
        String status,
        String startUserId,
        String startTime,
        String endTime,
        List<TrackNode> nodes) {
}
