package com.hris.flow.service;

import com.hris.common.api.R;
import com.hris.flow.client.OrgClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * 审批人解析（docs/04 §4，FLW-F-005）。
 *
 * <p>优先级链（MVP 简化）：指定人 &gt; 流程变量 assignee &gt; payload.supervisorEmpId（直属上级）
 * &gt; org 汇报线（Feign，降级）&gt; null（待指派队列，不阻塞不丢单）。</p>
 *
 * <p>完整优先级（指定人→岗位角色→直属上级→部门负责人→HRBP→表达式）属后续增量，汇报线依赖
 * hris-org 的 {@code /internal/org/reportline/{empId}}（预留契约 + 降级）。</p>
 */
@Service
public class AssigneeResolver {

    private static final Logger log = LoggerFactory.getLogger(AssigneeResolver.class);

    private final OrgClient orgClient;

    public AssigneeResolver(OrgClient orgClient) {
        this.orgClient = orgClient;
    }

    /**
     * 解析审批人用户 ID（字符串形式，供 Flowable assignee 变量使用）。解析失败返回 null。
     */
    public String resolve(Long explicitAssignee, Map<String, Object> variables,
                          Map<String, Object> payload, Long initiator) {
        String fromExplicit = asString(explicitAssignee);
        if (fromExplicit != null) {
            return fromExplicit;
        }
        if (variables != null) {
            String fromVar = asString(variables.get("assignee"));
            if (fromVar != null) {
                return fromVar;
            }
        }
        if (payload != null) {
            String fromSupervisor = asString(payload.get("supervisorEmpId"));
            if (fromSupervisor != null) {
                return fromSupervisor;
            }
        }
        if (initiator != null) {
            String fromReportline = resolveReportline(initiator);
            if (fromReportline != null) {
                return fromReportline;
            }
        }
        return null;
    }

    private String resolveReportline(Long initiator) {
        try {
            R<List<Long>> resp = orgClient.reportline(initiator);
            if (resp != null && resp.isSuccess() && resp.getData() != null && !resp.getData().isEmpty()) {
                return String.valueOf(resp.getData().get(0));
            }
        } catch (Exception e) {
            log.warn("org 汇报线解析失败，任务进入待指派: initiator={}, err={}", initiator, e.getMessage());
        }
        return null;
    }

    private String asString(Object value) {
        if (value == null) {
            return null;
        }
        String s = String.valueOf(value).trim();
        return s.isEmpty() || "null".equals(s) ? null : s;
    }
}
