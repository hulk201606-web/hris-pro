package com.hris.flow.dto;

/**
 * 待办/已办/我发起列表项（跨流程聚合，FLW-F-006/011）。
 *
 * <p>formSchemaJson：所属模型 flow_ext_model.form_schema_json 非空时随待办返回（前端可渲染）；
 * overdue：配置了超时时长（flow_ext_timeout_cfg）且任务已超期时为 true。</p>
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
        long durationInMillis,
        String formSchemaJson,
        boolean overdue) {
}
