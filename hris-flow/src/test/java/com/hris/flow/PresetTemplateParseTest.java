package com.hris.flow;

import org.flowable.engine.ProcessEngine;
import org.flowable.engine.ProcessEngineConfiguration;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.engine.repository.Deployment;
import org.flowable.task.api.Task;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.hris.flow.service.PresetTemplateSeeder;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * 17 条预置模板 XML 引擎级校验（FLW-F-019，docs/03-07 §3 清单）。
 *
 * <p>独立内存引擎（H2，不起 Spring 上下文）：逐条部署即完成 BPMN 解析 + 语义模型校验；
 * 并对含网关/表达式的关键模板启动实例，验证路由变量表达式
 * （Flowable 8 variables:getOrDefault）真实可求值。</p>
 */
class PresetTemplateParseTest {

    private static ProcessEngine engine;
    private static RepositoryService repositoryService;
    private static RuntimeService runtimeService;
    private static TaskService taskService;

    private static final List<String> PRESET_KEYS = PresetTemplateSeeder.PRESET_KEYS;

    @BeforeAll
    static void setUp() {
        engine = ProcessEngineConfiguration.createStandaloneInMemProcessEngineConfiguration()
                .buildProcessEngine();
        repositoryService = engine.getRepositoryService();
        runtimeService = engine.getRuntimeService();
        taskService = engine.getTaskService();
    }

    @AfterAll
    static void tearDown() {
        if (engine != null) {
            engine.close();
        }
    }

    @ParameterizedTest(name = "{0} 可部署（BPMN 解析 + 语义校验通过）")
    @ValueSource(strings = {
            "EMP_ONBOARD", "EMP_REGULAR", "EMP_TRANSFER", "EMP_RESIGN",
            "EMP_CONTRACT_RENEW", "EMP_PROFILE_CHG",
            "ATT_LEAVE", "ATT_OT", "ATT_TRIP", "ATT_FIX",
            "ORG_ADJUST", "HC_OVERSTAFF",
            "RCS_REQUISITION", "RCS_OFFER",
            "CMP_ADJUST", "CMP_PAYROLL",
            "KBD_PUBLISH"})
    void presetTemplateShouldBeDeployable(String key) {
        Deployment deployment = repositoryService.createDeployment()
                .addClasspathResource("processes/" + key + ".bpmn20.xml")
                .deploy();
        assertNotNull(deployment);
        assertTrue(deployment.getId() != null && !deployment.getId().isBlank());
    }

    @Test
    @DisplayName("ATT_LEAVE 天数阶梯：缺省变量走上级；days>7 走总监")
    void attLeaveLadderShouldRoute() {
        repositoryService.createDeployment()
                .addClasspathResource("processes/ATT_LEAVE.bpmn20.xml")
                .deploy();

        // 未传 days（发起方尚未注入路由变量）→ 缺省 0 天 → 上级节点，与单节点旧行为兼容
        var piDefault = runtimeService.startProcessInstanceByKey("ATT_LEAVE", "biz-default",
                java.util.Map.of("assignee", "5"));
        Task t1 = taskService.createTaskQuery().processInstanceId(piDefault.getId()).singleResult();
        assertEquals("上级审批", t1.getName());

        // days=10 → 总监节点（默认分支）
        var pi10 = runtimeService.startProcessInstanceByKey("ATT_LEAVE", "biz-10",
                java.util.Map.of("days", 10, "assignee", "5"));
        Task t2 = taskService.createTaskQuery().processInstanceId(pi10.getId()).singleResult();
        assertEquals("总监审批", t2.getName());
    }

    @Test
    @DisplayName("HC_OVERSTAFF 编制校验网关：exceeded=true 走分管，false 直达结束")
    void hcOverstaffGatewayShouldRoute() {
        repositoryService.createDeployment()
                .addClasspathResource("processes/HC_OVERSTAFF.bpmn20.xml")
                .deploy();

        var piOver = runtimeService.startProcessInstanceByKey("HC_OVERSTAFF", "biz-over",
                java.util.Map.of("exceeded", true));
        Task t1 = taskService.createTaskQuery().processInstanceId(piOver.getId()).singleResult();
        assertEquals("分管审批", t1.getName());

        // 未传 exceeded → 缺省 true（保守路由需审批）
        var piMissing = runtimeService.startProcessInstanceByKey("HC_OVERSTAFF", "biz-missing");
        Task t2 = taskService.createTaskQuery().processInstanceId(piMissing.getId()).singleResult();
        assertEquals("分管审批", t2.getName());

        var piOk = runtimeService.startProcessInstanceByKey("HC_OVERSTAFF", "biz-ok",
                java.util.Map.of("exceeded", false));
        long count = runtimeService.createProcessInstanceQuery()
                .processInstanceId(piOk.getId()).count();
        assertEquals(0L, count, "未超编应直达结束");
    }
}
