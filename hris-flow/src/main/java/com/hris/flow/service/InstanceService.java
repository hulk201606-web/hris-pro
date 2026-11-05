package com.hris.flow.service;

import com.hris.common.exception.BizException;
import com.hris.common.exception.ErrorCode;
import com.hris.common.trace.TraceContext;
import com.hris.flow.dto.StartInstanceRequest;
import com.hris.flow.dto.StartInstanceResponse;
import com.hris.flow.dto.TrackNode;
import com.hris.flow.dto.TrackResponse;
import com.hris.flow.entity.BizRelation;
import com.hris.flow.enums.InstanceStatus;
import com.hris.flow.mapper.BizRelationMapper;
import org.flowable.common.engine.impl.identity.Authentication;
import org.flowable.engine.HistoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.engine.history.HistoricActivityInstance;
import org.flowable.engine.history.HistoricProcessInstance;
import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.task.api.Task;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 流程实例服务：发起（幂等）/撤回/轨迹（FLW-F-004/016/011）。
 *
 * <p>多租户：Flowable 原生 tenant 字段（值=平台 tenantId），所有查询强制带 tenantId
 * （docs/04 §1）；网关层保证 tenantId 只来自 JWT。</p>
 */
@Service
public class InstanceService {

    private static final Logger log = LoggerFactory.getLogger(InstanceService.class);

    private final RuntimeService runtimeService;
    private final HistoryService historyService;
    private final BizRelationService bizRelationService;
    private final BizRelationMapper bizRelationMapper;
    private final AssigneeResolver assigneeResolver;
    private final EventOutboxService eventOutboxService;
    private final ApprovalCallbackDispatcher callbackDispatcher;
    private final CcService ccService;
    private final TaskService taskService;

    public InstanceService(RuntimeService runtimeService, HistoryService historyService,
                           BizRelationService bizRelationService, BizRelationMapper bizRelationMapper,
                           AssigneeResolver assigneeResolver, EventOutboxService eventOutboxService,
                           ApprovalCallbackDispatcher callbackDispatcher, CcService ccService,
                           TaskService taskService) {
        this.runtimeService = runtimeService;
        this.historyService = historyService;
        this.bizRelationService = bizRelationService;
        this.bizRelationMapper = bizRelationMapper;
        this.assigneeResolver = assigneeResolver;
        this.eventOutboxService = eventOutboxService;
        this.callbackDispatcher = callbackDispatcher;
        this.ccService = ccService;
        this.taskService = taskService;
    }

    /**
     * 发起流程实例（幂等：同 tenant+biz_type+biz_key 唯一）。
     */
    @Transactional(rollbackFor = Exception.class)
    public StartInstanceResponse start(StartInstanceRequest req) {
        Long tenantId = requireTenant();
        // 1. 应用层幂等查重（唯一约束兜底）
        if (bizRelationService.findActive(req.bizType(), req.bizKey()) != null) {
            throw new BizException(ErrorCode.IDEMPOTENT_CONFLICT, "该业务单已发起流程，请勿重复提交");
        }
        String procDefKey = resolveProcDefKey(req);
        // 2. 仅路由用非敏感变量进引擎（敏感字段不复制，docs/04 §6）
        Map<String, Object> vars = new HashMap<>();
        if (req.variables() != null) {
            vars.putAll(req.variables());
        }
        String assignee = assigneeResolver.resolve(req.assignee(), req.variables(), req.payload(), req.initiator());
        // 待指派语义：四级解析全部落空时也必须提供 assignee 变量（空串 → 任务无主创建）。
        // 若缺席该变量，模板里 flowable:assignee="${assignee}" 会在首个节点创建时抛
        // "Unknown property used in expression"，flow 发起 500，业务侧 fail-open 后
        // 单据永远停在 PROCESSING——待指派"不阻断不丢单"被实现打败（docs/19 §8.7-1）。
        // 无主任务不经认领不会出现在任何人的 todo，重新指派/认领能力另行登记。
        vars.put("assignee", assignee != null ? assignee : "");
        // 3. 记录发起人（Flowable startedBy），供「我发起的」查询
        String initiator = String.valueOf(resolveInitiator(req));
        // 兜底：Feign 系统发起（无登录用户）时 resolveInitiator 落 0——模板里
        // ${initiator} 表达式（如 EMP_REGULAR 自评节点 flowable:initiator）会把
        // 任务指给不存在的用户 0，既不在 todo 也不在无主池，成为死任务。
        // 此时用审批人解析值兜底，并显式放入 vars 保证 ${initiator} 可解析。
        if ("0".equals(initiator) && assignee != null) {
            initiator = assignee;
        }
        vars.put("initiator", initiator);
        ProcessInstance pi;
        try {
            Authentication.setAuthenticatedUserId(initiator);
            pi = runtimeService.startProcessInstanceByKeyAndTenantId(
                    procDefKey, req.bizKey(), vars, String.valueOf(tenantId));
        } finally {
            Authentication.setAuthenticatedUserId(null);
        }
        // 4. 落映射（唯一约束兜底幂等；竞态时引擎实例随事务回滚）
        BizRelation rel = new BizRelation();
        rel.setProcInstId(pi.getId());
        rel.setBizType(req.bizType());
        rel.setBizKey(req.bizKey());
        rel.setStatus(InstanceStatus.RUNNING.name());
        try {
            bizRelationMapper.insert(rel);
        } catch (DuplicateKeyException e) {
            throw new BizException(ErrorCode.IDEMPOTENT_CONFLICT, "该业务单已发起流程，请勿重复提交");
        }
        // 5. 事件出站（事务消息兜底）
        eventOutboxService.publish(EventOutboxService.TOPIC_INSTANCE_STARTED, tenantId, req.bizKey(),
                Map.of("procInstId", pi.getId(), "bizType", req.bizType(), "bizKey", req.bizKey()));
        // 6. 抄送写入（FLW-F-011）：按发起节点（当前活动任务）记录，重复忽略
        if (req.ccEmpIds() != null && !req.ccEmpIds().isEmpty()) {
            ccService.recordOnStart(pi.getId(), firstTaskKey(pi.getId()), req.ccEmpIds());
        }
        return new StartInstanceResponse(pi.getId(), req.bizType(), req.bizKey(), InstanceStatus.RUNNING.name());
    }

    /** 发起节点 Key：实例当前活动任务的节点 Key（无任务时兜底 start）。 */
    private String firstTaskKey(String procInstId) {
        List<Task> tasks = taskService.createTaskQuery().processInstanceId(procInstId).list();
        return tasks.isEmpty() ? "start" : tasks.get(0).getTaskDefinitionKey();
    }

    /**
     * 撤回流程实例（仅当无已处理节点时允许，docs/04 §5），以 WITHDRAWN 语义通知业务域。
     */
    @Transactional(rollbackFor = Exception.class)
    public void withdraw(String instanceId, String reason) {
        requireTenant();
        BizRelation rel = bizRelationService.requireByProcInstId(instanceId);
        if (!InstanceStatus.RUNNING.name().equals(rel.getStatus())) {
            throw new BizException(ErrorCode.FLOW_STATE_CONFLICT, "流程已结束，不可撤回");
        }
        if (hasProcessedNode(instanceId)) {
            throw new BizException(ErrorCode.FLOW_STATE_CONFLICT, "存在已处理节点，不可撤回");
        }
        runtimeService.deleteProcessInstance(instanceId, "WITHDRAWN:" + (reason == null ? "" : reason));
        bizRelationService.updateStatus(rel, InstanceStatus.TERMINATED);
        eventOutboxService.publish(EventOutboxService.TOPIC_INSTANCE_FINISHED, rel.getTenantId(), rel.getBizKey(),
                Map.of("procInstId", instanceId, "bizType", rel.getBizType(), "bizKey", rel.getBizKey(),
                        "outcome", "WITHDRAWN"));
        callbackDispatcher.dispatchOnTerminal(rel.getBizType(), rel.getBizKey(), rel.getTenantId(),
                false, "WITHDRAWN", null, instanceId);
    }

    /**
     * 流程轨迹图数据：节点 + 状态 + 耗时。
     */
    public TrackResponse track(String instanceId) {
        Long tenantId = requireTenant();
        HistoricProcessInstance hpi = historyService.createHistoricProcessInstanceQuery()
                .processInstanceId(instanceId)
                .processInstanceTenantId(String.valueOf(tenantId))
                .singleResult();
        if (hpi == null) {
            throw new BizException(ErrorCode.FLOW_NOT_FOUND, "流程实例不存在");
        }
        List<HistoricActivityInstance> acts = historyService.createHistoricActivityInstanceQuery()
                .processInstanceId(instanceId)
                .orderByHistoricActivityInstanceStartTime().asc()
                .list();
        List<TrackNode> nodes = new ArrayList<>();
        for (HistoricActivityInstance a : acts) {
            nodes.add(new TrackNode(
                    a.getActivityId(), a.getActivityName(), a.getActivityType(), a.getAssignee(),
                    toNodeStatus(a), fmt(a.getStartTime()), fmt(a.getEndTime()),
                    a.getDurationInMillis() == null ? 0L : a.getDurationInMillis()));
        }
        String status = hpi.getEndTime() == null ? InstanceStatus.RUNNING.name() : InstanceStatus.FINISHED.name();
        return new TrackResponse(instanceId, status, hpi.getStartUserId(),
                fmt(hpi.getStartTime()), fmt(hpi.getEndTime()), nodes);
    }

    private boolean hasProcessedNode(String instanceId) {
        List<HistoricActivityInstance> tasks = historyService.createHistoricActivityInstanceQuery()
                .processInstanceId(instanceId)
                .activityType("userTask")
                .list();
        return tasks.stream().anyMatch(t -> t.getEndTime() != null);
    }

    private String toNodeStatus(HistoricActivityInstance a) {
        if (a.getEndTime() != null) {
            return "COMPLETED";
        }
        if (a.getStartTime() != null) {
            return "ACTIVE";
        }
        return "PENDING";
    }

    private String resolveProcDefKey(StartInstanceRequest req) {
        return req.procDefKey() == null || req.procDefKey().isBlank() ? req.bizType() : req.procDefKey();
    }

    private Long resolveInitiator(StartInstanceRequest req) {
        if (req.initiator() != null) {
            return req.initiator();
        }
        Long uid = TraceContext.getUserId();
        return uid == null ? 0L : uid;
    }

    private Long requireTenant() {
        Long tenantId = TraceContext.getTenantId();
        if (tenantId == null || tenantId == 0L) {
            throw new BizException(ErrorCode.TENANT_INVALID);
        }
        return tenantId;
    }

    private String fmt(java.util.Date d) {
        return d == null ? null : java.time.Instant.ofEpochMilli(d.getTime()).toString();
    }
}
