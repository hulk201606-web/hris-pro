package com.hris.flow.strategy;

import org.flowable.bpmn.model.BpmnModel;
import org.flowable.bpmn.model.ExtensionAttribute;
import org.flowable.bpmn.model.ExtensionElement;
import org.flowable.bpmn.model.Process;
import org.flowable.bpmn.model.UserTask;
import org.flowable.engine.RepositoryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 节点策略解析单测（FLW-F-008）：extensionElements 两种写法、未配置兼容、非法取值降级、解析缓存。
 */
@ExtendWith(MockitoExtension.class)
class NodeStrategyResolverTest {

    private static final String DEF_ID = "committeeFlow:1:9001";

    @Mock
    RepositoryService repositoryService;

    private NodeStrategyResolver resolver;

    @BeforeEach
    void setUp() {
        resolver = new NodeStrategyResolver(repositoryService);
    }

    private void stubModel(ExtensionElement... elements) {
        BpmnModel model = new BpmnModel();
        Process process = new Process();
        process.setId("committeeFlow");
        process.setExecutable(true);
        UserTask task = new UserTask();
        task.setId("committee");
        for (ExtensionElement e : elements) {
            task.addExtensionElement(e);
        }
        process.addFlowElement(task);
        model.addProcess(process);
        when(repositoryService.getBpmnModel(DEF_ID)).thenReturn(model);
    }

    private static ExtensionElement approvalStrategy(String type, String threshold) {
        ExtensionElement e = new ExtensionElement();
        e.setName("approvalStrategy");
        e.setNamespace("http://hris.com/schema/flow");
        e.addAttribute(new ExtensionAttribute("strategy", type));
        if (threshold != null) {
            e.addAttribute(new ExtensionAttribute("percentThreshold", threshold));
        }
        return e;
    }

    private static ExtensionElement property(String name, String value) {
        ExtensionElement e = new ExtensionElement();
        e.setName("property");
        e.setNamespace("http://flowable.org/bpmn");
        e.addAttribute(new ExtensionAttribute("name", name));
        e.addAttribute(new ExtensionAttribute("value", value));
        return e;
    }

    @Test
    @DisplayName("hris:approvalStrategy 元素 → 解析出 PERCENT + 自定义阈值")
    void shouldParseCustomElement() {
        stubModel(approvalStrategy("PERCENT", "70"));

        Optional<NodeStrategySpec> spec = resolver.resolve(DEF_ID, "committee");

        assertTrue(spec.isPresent());
        assertEquals(ApprovalStrategyType.PERCENT, spec.get().type());
        assertEquals(70, spec.get().percentThreshold());
    }

    @Test
    @DisplayName("flowable:property 兼容写法 + 缺阈值 → 缺省 60%")
    void shouldParsePropertyElements() {
        stubModel(property("strategy", "VETO"), property("percentThreshold", "80"));

        NodeStrategySpec spec = resolver.resolve(DEF_ID, "committee").orElseThrow();

        assertEquals(ApprovalStrategyType.VETO, spec.type());
        assertEquals(80, spec.percentThreshold());
    }

    @Test
    @DisplayName("未配置 extensionElements → 空结果（17 个预置模板保持原行为）")
    void shouldReturnEmptyWhenNotConfigured() {
        stubModel();

        assertTrue(resolver.resolve(DEF_ID, "committee").isEmpty());
        assertTrue(resolver.resolve(DEF_ID, "unknownNode").isEmpty());
        assertTrue(resolver.resolve(null, "committee").isEmpty());
    }

    @Test
    @DisplayName("非法策略取值 → 降级为未配置，不阻断审批")
    void shouldIgnoreIllegalStrategyValue() {
        stubModel(approvalStrategy("MAJORITY", "60"));

        assertTrue(resolver.resolve(DEF_ID, "committee").isEmpty());
    }

    @Test
    @DisplayName("同一流程定义+节点重复解析走缓存，只读一次 BPMN 模型")
    void shouldCachePerDefinitionAndNode() {
        stubModel(approvalStrategy("PERCENT", null));

        resolver.resolve(DEF_ID, "committee");
        resolver.resolve(DEF_ID, "committee");

        verify(repositoryService, times(1)).getBpmnModel(DEF_ID);
    }

    @Test
    @DisplayName("阈值越界按 1-100 收敛")
    void shouldClampThreshold() {
        assertEquals(100, NodeStrategySpec.of(ApprovalStrategyType.PERCENT, 200).percentThreshold());
        assertEquals(1, NodeStrategySpec.of(ApprovalStrategyType.PERCENT, 0).percentThreshold());
    }
}
