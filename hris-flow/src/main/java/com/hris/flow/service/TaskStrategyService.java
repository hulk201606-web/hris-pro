package com.hris.flow.service;

import com.hris.flow.strategy.ApprovalStrategy;
import com.hris.flow.strategy.ApprovalStrategyFactory;
import com.hris.flow.strategy.NodeStrategyResolver;
import com.hris.flow.strategy.NodeStrategySpec;
import com.hris.flow.strategy.StrategyDecision;
import com.hris.flow.strategy.StrategyOutcome;
import com.hris.flow.strategy.StrategyVote;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.task.api.Task;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 会签策略评估与驱动（FLW-F-008）。
 *
 * <p>职责：读取节点策略 → 汇总计票（并行/串行多实例均按 nrOfInstances 归口）→ 调策略 → 写回流程变量
 * → 达标时关停同节点剩余会签任务。策略本身只做纯计算，引擎交互集中在本类。</p>
 *
 * <p>变量约定（流程级，供 BPMN 条件表达式与审计使用）：</p>
 * <ul>
 *   <li>{@code approvedCount} / {@code totalCount} / {@code vetoed}：当前节点计票镜像；</li>
 *   <li>{@code flow_approved_<node>} / {@code flow_total_<node>} / {@code flow_vetoed_<node>}：
 *       节点级计票，多节点并发时互不覆盖。</li>
 * </ul>
 *
 * <p>未配置策略的节点直接返回空，动作服务保持原行为（17 个预置模板零改动）。</p>
 */
@Service
public class TaskStrategyService {

    private static final Logger log = LoggerFactory.getLogger(TaskStrategyService.class);

    private static final String VAR_APPROVED = "approvedCount";
    private static final String VAR_TOTAL = "totalCount";
    private static final String VAR_VETOED = "vetoed";

    private final TaskService taskService;
    private final RuntimeService runtimeService;
    private final NodeStrategyResolver resolver;
    private final ApprovalStrategyFactory strategyFactory;

    public TaskStrategyService(TaskService taskService, RuntimeService runtimeService,
                               NodeStrategyResolver resolver, ApprovalStrategyFactory strategyFactory) {
        this.taskService = taskService;
        this.runtimeService = runtimeService;
        this.resolver = resolver;
        this.strategyFactory = strategyFactory;
    }

    /**
     * 评估一次投票（agree=true 同意 / false 否决）。
     *
     * @return 节点已配置策略时的评估结果；未配置返回 {@link Optional#empty()}（调用方按原逻辑处理）
     */
    public Optional<StrategyOutcome> evaluate(Task task, boolean agree) {
        NodeStrategySpec spec = resolver.resolve(task.getProcessDefinitionId(), task.getTaskDefinitionKey())
                .orElse(null);
        if (spec == null) {
            return Optional.empty();
        }
        String procInstId = task.getProcessInstanceId();
        String nodeKey = task.getTaskDefinitionKey();
        String approvedKey = nodeVar("approved", nodeKey);
        String vetoedKey = nodeVar("vetoed", nodeKey);
        String totalKey = nodeVar("total", nodeKey);

        int approvedBefore = intVar(procInstId, approvedKey);
        int vetoedBefore = intVar(procInstId, vetoedKey);
        int total = resolveTotal(task, procInstId, nodeKey, totalKey, approvedBefore + vetoedBefore);

        int approved = approvedBefore + (agree ? 1 : 0);
        int vetoed = vetoedBefore + (agree ? 0 : 1);
        int remaining = Math.max(0, total - approved - vetoed);

        StrategyVote vote = new StrategyVote(spec.percentThreshold(), approved, total, remaining, vetoed > 0);
        ApprovalStrategy strategy = strategyFactory.require(spec.type());
        StrategyDecision decision = strategy.decide(vote);
        StrategyOutcome outcome = StrategyOutcome.of(decision, spec, vote);

        writeVars(procInstId, nodeKey, approvedKey, totalKey, vetoedKey, approved, total, vetoed > 0);
        if (decision == StrategyDecision.PASS && remaining > 0) {
            closeRemainingTasks(task, nodeKey, outcome.message());
        }
        log.info("会签策略评估: procInst={}, node={}, decision={}, detail={}",
                procInstId, nodeKey, decision, outcome.message());
        return Optional.of(outcome);
    }

    /** 总票数：多实例取 nrOfInstances，否则沿用已锁定的节点总票数，最后按节点当前任务数推断。 */
    private int resolveTotal(Task task, String procInstId, String nodeKey, String totalKey, int votedBefore) {
        Integer miTotal = intVarOrNull(task.getExecutionId(), "nrOfInstances");
        if (miTotal != null && miTotal > 0) {
            return miTotal;
        }
        int stored = intVar(procInstId, totalKey);
        if (stored > 0) {
            return stored;
        }
        long active = taskService.createTaskQuery().processInstanceId(procInstId)
                .taskDefinitionKey(nodeKey).count();
        return (int) Math.max(active, votedBefore + 1L);
    }

    /** 达标提前通过：以系统语义完成同节点剩余任务，避免继续等待未投的票。 */
    private void closeRemainingTasks(Task current, String nodeKey, String reason) {
        List<Task> others = taskService.createTaskQuery()
                .processInstanceId(current.getProcessInstanceId())
                .taskDefinitionKey(nodeKey)
                .list();
        for (Task t : others) {
            if (t.getId().equals(current.getId())) {
                continue;
            }
            try {
                Map<String, Object> vars = new LinkedHashMap<>();
                vars.put("flowAutoPassed", true);
                vars.put("flowAutoPassedNode", nodeKey);
                vars.put("flowAutoPassedReason", reason);
                taskService.complete(t.getId(), vars);
                log.info("会签比例达标，关停剩余任务: taskId={}, node={}", t.getId(), nodeKey);
            } catch (RuntimeException e) {
                log.warn("关停剩余会签任务失败（可能已被并发处理）: taskId={}, err={}", t.getId(), e.getMessage());
            }
        }
    }

    private void writeVars(String procInstId, String nodeKey, String approvedKey, String totalKey,
                           String vetoedKey, int approved, int total, boolean vetoed) {
        Map<String, Object> vars = new LinkedHashMap<>();
        vars.put(approvedKey, approved);
        vars.put(totalKey, total);
        vars.put(vetoedKey, vetoed ? 1 : 0);
        // 节点级变量随实例推进可能同名的多节点覆盖，镜像仅用于「当前节点」的条件表达式与审计
        vars.put(VAR_APPROVED, approved);
        vars.put(VAR_TOTAL, total);
        vars.put(VAR_VETOED, vetoed);
        runtimeService.setVariables(procInstId, vars);
    }

    private static String nodeVar(String prefix, String nodeKey) {
        return "flow_" + prefix + "_" + nodeKey;
    }

    private int intVar(String executionId, String name) {
        Integer v = intVarOrNull(executionId, name);
        return v == null ? 0 : v;
    }

    private Integer intVarOrNull(String executionId, String name) {
        if (executionId == null) {
            return null;
        }
        Object v = runtimeService.getVariable(executionId, name);
        if (v instanceof Number n) {
            return n.intValue();
        }
        if (v instanceof String s && !s.isBlank()) {
            try {
                return Integer.valueOf(s.trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }
}
