package com.hris.flow;

import com.hris.flow.dto.ModelDtos;
import com.hris.flow.entity.FlowModel;
import com.hris.flow.enums.DeployState;
import com.hris.flow.mapper.FlowModelMapper;
import com.hris.flow.service.ModelService;
import com.hris.flow.service.PresetTemplateSeeder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 预置模板种子幂等性测试（FLW-F-019 / docs/19 §8.7-6）。
 */
@ExtendWith(MockitoExtension.class)
class PresetTemplateSeederTest {

    @Mock
    ModelService modelService;

    @Mock
    FlowModelMapper flowModelMapper;

    @AfterEach
    void tearDown() {
        // seed 必须清理租户上下文，避免污染线程
        assertNull(com.hris.common.trace.TraceContext.getTenantId());
    }

    private PresetTemplateSeeder seeder(String tenants) {
        return new PresetTemplateSeeder(modelService, flowModelMapper, tenants);
    }

    private FlowModel model(long id, String deployState) {
        FlowModel m = new FlowModel();
        m.setId(id);
        m.setProcDefKey("ATT_OT");
        m.setVersion(1);
        m.setDeployState(deployState);
        return m;
    }

    @Test
    @DisplayName("未配置 seed-tenants 时跳过安装（不触碰 DB 与 Flowable）")
    void shouldSkipWhenNoTenantsConfigured() {
        seeder("").run(null);
        verify(modelService, never()).create(any());
        verify(modelService, never()).deploy(any());
    }

    @Test
    @DisplayName("模型不存在时逐条 create + deploy（17 条预置 + GENERIC_APPROVAL）")
    void shouldCreateAndDeployWhenAbsent() {
        when(flowModelMapper.selectList(any())).thenReturn(List.of());
        when(modelService.create(any())).thenReturn(
                new ModelDtos.ModelResponse(1L, "ATT_OT", 1, "DEPLOYED", null));

        seeder("100001").run(null);

        ArgumentCaptor<ModelDtos.ModelCreateRequest> captor =
                ArgumentCaptor.forClass(ModelDtos.ModelCreateRequest.class);
        verify(modelService, times(PresetTemplateSeeder.seedKeys().size())).create(captor.capture());
        verify(modelService, times(PresetTemplateSeeder.seedKeys().size())).deploy(1L);
        assertEquals(PresetTemplateSeeder.PRESET_KEYS.size(), 17);
        assertEquals(18, PresetTemplateSeeder.seedKeys().size());
    }

    @Test
    @DisplayName("模型已存在且 DEPLOYED → 跳过（幂等，不重复入库部署）")
    void shouldSkipWhenModelDeployed() {
        when(flowModelMapper.selectList(any())).thenReturn(List.of(model(9L, DeployState.DEPLOYED.name())));

        seeder("100001").run(null);

        verify(modelService, never()).create(any());
        verify(modelService, never()).deploy(any());
    }

    @Test
    @DisplayName("历史 DRAFT 遗留（上次部署失败）→ 启动时补部署")
    void shouldRedeployDraftModel() {
        // 首个 key（GENERIC_APPROVAL）查询命中 DRAFT 模型补部署，其余 key 返回空照常 create
        when(flowModelMapper.selectList(any()))
                .thenReturn(List.of(model(9L, DeployState.DRAFT.name())))
                .thenReturn(List.of());

        seeder("100001").run(null);

        verify(modelService, times(PresetTemplateSeeder.seedKeys().size() - 1)).create(any());
        verify(modelService, times(1)).deploy(9L);
    }

    @Test
    @DisplayName("单条失败不阻断其余模板（失败跳过，不重试）")
    void shouldContinueWhenOneTemplateFails() {
        when(flowModelMapper.selectList(any())).thenReturn(List.of());
        when(modelService.create(any())).thenAnswer(inv -> {
            ModelDtos.ModelCreateRequest req = inv.getArgument(0);
            if ("ATT_LEAVE".equals(req.procDefKey())) {
                throw new IllegalStateException("engine down");
            }
            return new ModelDtos.ModelResponse(1L, req.procDefKey(), 1, "DEPLOYED", null);
        });

        seeder("100001").run(null);

        // 除 ATT_LEAVE 外的 17 条仍被处理
        verify(modelService, times(PresetTemplateSeeder.seedKeys().size() - 1)).deploy(1L);
    }

    @Test
    @DisplayName("多租户与非法租户项：合法租户逐个安装，非法项忽略")
    void shouldParseTenantsAndIgnoreInvalid() {
        when(flowModelMapper.selectList(any())).thenReturn(List.of());
        when(modelService.create(any())).thenReturn(
                new ModelDtos.ModelResponse(1L, "KBD_PUBLISH", 1, "DEPLOYED", null));

        seeder(" 100001, abc, 100002, 100001 , 0, -3").run(null);

        // 2 个合法租户 × 18 条模板
        verify(modelService, times(2 * PresetTemplateSeeder.seedKeys().size())).create(any());
        verify(modelService, times(2 * PresetTemplateSeeder.seedKeys().size())).deploy(1L);
    }
}
