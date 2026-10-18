package com.hris.flow.config;

import javax.sql.DataSource;

import org.flowable.engine.HistoryService;
import org.flowable.engine.ProcessEngine;
import org.flowable.engine.ProcessEngineConfiguration;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.engine.impl.cfg.StandaloneProcessEngineConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 降级预案 A（docs/04 §8）：当 flowable-spring-boot-starter-process 在 Boot 4.0 下
 * 自动装配失败（ProcessEngine bean 不存在）时，启用本配置手工装配引擎。
 *
 * <p>仅在 {@code ProcessEngine} bean 缺失时生效，正常装配路径下不参与。</p>
 *
 * <p>说明：starter 仅是自动配置壳，引擎核心（ProcessEngineConfigurationImpl）不依赖 Boot。
 * 若 Jackson 2→3 迁移或事务语义仍不兼容，进一步降级预案 B：hris-flow 单服务锁 Boot 3.5.x。</p>
 */
@Configuration
@ConditionalOnMissingBean(ProcessEngine.class)
public class FlowableManualConfig {

    @Bean
    public ProcessEngine processEngine(DataSource dataSource) {
        ProcessEngineConfiguration cfg = new StandaloneProcessEngineConfiguration()
                .setDataSource(dataSource)
                .setDatabaseSchemaUpdate("true");
        return cfg.buildProcessEngine();
    }

    @Bean
    public RepositoryService repositoryService(ProcessEngine processEngine) {
        return processEngine.getRepositoryService();
    }

    @Bean
    public RuntimeService runtimeService(ProcessEngine processEngine) {
        return processEngine.getRuntimeService();
    }

    @Bean
    public TaskService taskService(ProcessEngine processEngine) {
        return processEngine.getTaskService();
    }

    @Bean
    public HistoryService historyService(ProcessEngine processEngine) {
        return processEngine.getHistoryService();
    }
}
