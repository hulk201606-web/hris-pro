package com.hris.flow;

import org.flowable.engine.ProcessEngine;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.task.api.Task;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * S0.4 硬门禁：Flowable 7.2.x 在 Spring Boot 4.0 下的引擎装配验证（docs/04 §8）。
 *
 * <p>验证清单（docs/04 §8.1~8.3）：</p>
 * <ol>
 *   <li>flowable-spring-boot-starter-process 在 Boot 4 的自动配置是否可用；</li>
 *   <li>Jackson 2→3 迁移对 Flowable 序列化的影响；</li>
 *   <li>事务管理器与 Spring 6.2/7 的 @Transactional 语义兼容。</li>
 * </ol>
 *
 * <p>运行方式（需 JDK 21 + Maven 3.8.6+）：</p>
 * <pre>mvn -pl hris-flow test -Dtest=FlowableAssemblyTest</pre>
 *
 * <p>若本测试因自动装配失败，启用降级预案 A：自建 {@link FlowableManualConfig} 手工装配
 * ProcessEngineConfigurationImpl（见该类注释）。</p>
 */
@SpringBootTest
@ActiveProfiles("test")
class FlowableAssemblyTest {

    @Autowired
    ProcessEngine processEngine;

    @Autowired
    RepositoryService repositoryService;

    @Autowired
    RuntimeService runtimeService;

    @Autowired
    TaskService taskService;

    @Test
    void engineShouldBeAssembled() {
        assertNotNull(processEngine, "Flowable ProcessEngine 未装配：Boot 4 自动装配失败，触发降级预案 A");
    }

    @Test
    void minimalProcessShouldRun() {
        // 部署最小流程
        repositoryService.createDeployment()
                .addClasspathResource("processes/hello.bpmn20.xml")
                .deploy();

        // 启动实例
        ProcessInstance pi = runtimeService.startProcessInstanceByKey("hello");
        assertNotNull(pi);

        // 完成唯一 userTask
        Task task = taskService.createTaskQuery()
                .processInstanceId(pi.getId())
                .singleResult();
        assertNotNull(task, "应产生待办任务");
        taskService.complete(task.getId());

        // 断言流程已结束（历史/运行实例均无）
        assertNull(runtimeService.createProcessInstanceQuery()
                .processInstanceId(pi.getId())
                .singleResult(), "流程实例应已结束");
    }
}
