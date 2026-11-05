package com.hris.flow.strategy;

import org.flowable.bpmn.model.BpmnModel;
import org.flowable.bpmn.model.ExtensionAttribute;
import org.flowable.bpmn.model.ExtensionElement;
import org.flowable.bpmn.model.FlowElement;
import org.flowable.engine.RepositoryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 节点会签策略解析器（FLW-F-008）：从 BPMN 用户任务 extensionElements 读取
 * {@code strategy} / {@code percentThreshold}，按 (流程定义ID + 节点ID) 缓存。
 *
 * <p><b>兼容性</b>：未配置 extensionElements 的节点一律返回 {@link Optional#empty()}，
 * 任务动作保持原有行为，17 个预置模板无需改动。</p>
 *
 * <p><b>MVP 边界</b>：配置入口目前只到 BPMN 部署包（解析即缓存，重新部署换版本会换
 * processDefinitionId 故自动失效）。管理端在线配置节点策略需等设计器（批 12）落地后
 * 以「部署前改写 extensionElements」的方式补齐。</p>
 */
@Service
public class NodeStrategyResolver {

    private static final Logger log = LoggerFactory.getLogger(NodeStrategyResolver.class);

    /** 自定义扩展元素名（见 {@link NodeStrategySpec}） */
    private static final String ELEMENT_APPROVAL_STRATEGY = "approvalStrategy";
    /** Flowable 通用属性元素名，作为兼容写法 */
    private static final String ELEMENT_PROPERTY = "property";

    private final RepositoryService repositoryService;
    private final Map<String, Optional<NodeStrategySpec>> cache = new ConcurrentHashMap<>();

    public NodeStrategyResolver(RepositoryService repositoryService) {
        this.repositoryService = repositoryService;
    }

    /** 解析节点策略；未配置返回空。结果（含空结果）按定义版本缓存。 */
    public Optional<NodeStrategySpec> resolve(String processDefinitionId, String nodeKey) {
        if (processDefinitionId == null || processDefinitionId.isBlank()
                || nodeKey == null || nodeKey.isBlank()) {
            return Optional.empty();
        }
        return cache.computeIfAbsent(processDefinitionId + '#' + nodeKey,
                k -> Optional.ofNullable(load(processDefinitionId, nodeKey)));
    }

    /** 清空缓存（流程定义重新部署或单测使用）。 */
    public void evictAll() {
        cache.clear();
    }

    private NodeStrategySpec load(String processDefinitionId, String nodeKey) {
        BpmnModel model;
        try {
            model = repositoryService.getBpmnModel(processDefinitionId);
        } catch (RuntimeException e) {
            log.warn("读取 BPMN 模型失败，节点策略按未配置处理: procDefId={}, node={}, err={}",
                    processDefinitionId, nodeKey, e.getMessage());
            return null;
        }
        if (model == null) {
            return null;
        }
        FlowElement element = model.getFlowElement(nodeKey);
        if (element == null || element.getExtensionElements() == null) {
            return null;
        }
        return parse(element.getExtensionElements());
    }

    /** 从扩展元素集合解析策略；无 strategy 或取值非法时返回 null（按未配置处理）。 */
    static NodeStrategySpec parse(Map<String, List<ExtensionElement>> extensionElements) {
        Map<String, String> props = new HashMap<>();
        for (ExtensionElement el : flatNamed(extensionElements, ELEMENT_PROPERTY)) {
            String name = attr(el, "name");
            String value = attr(el, "value");
            if (name != null && value != null) {
                props.putIfAbsent(name.trim(), value.trim());
            }
        }
        for (ExtensionElement el : flatNamed(extensionElements, ELEMENT_APPROVAL_STRATEGY)) {
            String strategy = attr(el, "strategy");
            if (strategy != null) {
                props.putIfAbsent("strategy", strategy.trim());
            }
            String threshold = attr(el, "percentThreshold");
            if (threshold != null) {
                props.putIfAbsent("percentThreshold", threshold.trim());
            }
        }
        String rawType = props.get("strategy");
        if (rawType == null) {
            return null;
        }
        Optional<ApprovalStrategyType> type = ApprovalStrategyType.parse(rawType);
        if (type.isEmpty()) {
            log.warn("节点会签策略取值非法，按未配置处理: strategy={}", rawType);
            return null;
        }
        return NodeStrategySpec.of(type.get(), parseInt(props.get("percentThreshold")));
    }

    private static List<ExtensionElement> flatNamed(Map<String, List<ExtensionElement>> elements, String name) {
        if (elements == null) {
            return List.of();
        }
        List<ExtensionElement> list = elements.get(name);
        return list == null ? List.of() : list;
    }

    /** 取扩展元素属性值（忽略命名空间，兼容 hris:/flowable: 前缀）。 */
    private static String attr(ExtensionElement element, String name) {
        if (element == null || element.getAttributes() == null) {
            return null;
        }
        List<ExtensionAttribute> attrs = element.getAttributes().get(name);
        if (attrs == null || attrs.isEmpty()) {
            return null;
        }
        for (ExtensionAttribute a : attrs) {
            if (a.getValue() != null && !a.getValue().isBlank()) {
                return a.getValue();
            }
        }
        return null;
    }

    private static Integer parseInt(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return Integer.valueOf(raw.trim());
        } catch (NumberFormatException e) {
            log.warn("percentThreshold 非数字，按缺省 {} 处理: {}", NodeStrategySpec.DEFAULT_PERCENT_THRESHOLD, raw);
            return null;
        }
    }
}
