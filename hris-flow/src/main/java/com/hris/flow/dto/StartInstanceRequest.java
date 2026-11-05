package com.hris.flow.dto;

import jakarta.validation.constraints.NotBlank;

import java.util.List;
import java.util.Map;

/**
 * 发起流程请求（FLW-F-004）。
 *
 * <p>敏感字段（薪酬数字等）不进入 variables / payload 复制，flow 仅缓存路由用非敏感摘要
 * （docs/04 §6）。payload 为业务侧快照，flow 不落库（业务服务为唯一事实源）。</p>
 */
public record StartInstanceRequest(
        @NotBlank(message = "bizType 不能为空") String bizType,
        @NotBlank(message = "bizKey 不能为空") String bizKey,
        /** 流程定义 Key，缺省取 bizType */
        String procDefKey,
        /** 业务快照（不落库，仅用于审批人解析等路由摘要） */
        Map<String, Object> payload,
        /** 路由变量（非敏感） */
        Map<String, Object> variables,
        /** 发起人用户 ID，缺省取当前登录用户 */
        Long initiator,
        /** 指定审批人（优先级最高，docs/04 §4 优先级链首位） */
        Long assignee,
        /** 抄送人用户 ID 列表（FLW-F-011，可选：实例启动时按发起节点写入抄送表） */
        List<Long> ccEmpIds) {
}
