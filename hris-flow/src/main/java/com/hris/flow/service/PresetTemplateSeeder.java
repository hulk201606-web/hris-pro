package com.hris.flow.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hris.common.trace.TraceContext;
import com.hris.flow.dto.ModelDtos;
import com.hris.flow.entity.FlowModel;
import com.hris.flow.enums.DeployState;
import com.hris.flow.mapper.FlowModelMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * 预置流程模板种子（FLW-F-019）：启动时对预置模板逐一「不存在则入库 + 部署」，已存在跳过（幂等）。
 *
 * <p>17 条预置模板以 docs/03-07 §3 权威清单为准（key 与各域 bizType 一致）；
 * GENERIC_APPROVAL 为 MVP 已有模板，纳入幂等兼容（已存在即跳过，见 docs/19 §8.7-6）。</p>
 *
 * <p>多租户：flow_db 为独立库（无租户主档 auth_tenant，跨库不可读），无法自行枚举租户；
 * 安装租户列表由 {@code hris.flow.seed-tenants} 配置注入（如 demo 租户 100001），逐租户设置
 * TraceContext 后复用 {@link ModelService} 的 create/deploy（tenant_id 由租户插件自动注入，
 * Flowable 部署带对应 tenantId，保证 startProcessInstanceByKeyAndTenantId 可路由）。</p>
 *
 * <p>容错：任何模板失败仅 log.error 跳过该条继续其余，不重试、不阻断应用启动；
 * 历史遗留 DRAFT 模型（上次部署失败）启动时自动补部署。</p>
 */
@Component
public class PresetTemplateSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(PresetTemplateSeeder.class);

    /** docs/03-07 §3 预置清单 + PRF_APPEAL（PRF 发起侧接入所需，2026-11-05 补充），顺序即安装顺序。 */
    public static final List<String> PRESET_KEYS = List.of(
            "EMP_ONBOARD", "EMP_REGULAR", "EMP_TRANSFER", "EMP_RESIGN",
            "EMP_CONTRACT_RENEW", "EMP_PROFILE_CHG",
            "ATT_LEAVE", "ATT_OT", "ATT_TRIP", "ATT_FIX",
            "ORG_ADJUST", "HC_OVERSTAFF",
            "RCS_REQUISITION", "RCS_OFFER",
            "CMP_ADJUST", "CMP_PAYROLL",
            "PRF_APPEAL", "KBD_PUBLISH");

    /** MVP 已有的通用模板，一并纳入幂等兼容。 */
    public static final String GENERIC_KEY = "GENERIC_APPROVAL";

    private final ModelService modelService;
    private final FlowModelMapper flowModelMapper;
    private final String seedTenants;

    public PresetTemplateSeeder(ModelService modelService, FlowModelMapper flowModelMapper,
                                @Value("${hris.flow.seed-tenants:}") String seedTenants) {
        this.modelService = modelService;
        this.flowModelMapper = flowModelMapper;
        this.seedTenants = seedTenants;
    }

    @Override
    public void run(ApplicationArguments args) {
        List<Long> tenants = parseTenants(seedTenants);
        if (tenants.isEmpty()) {
            log.info("未配置 hris.flow.seed-tenants，跳过预置流程模板安装");
            return;
        }
        for (Long tenantId : tenants) {
            try {
                TraceContext.setTenantId(tenantId);
                for (String key : seedKeys()) {
                    try {
                        seedOne(key);
                    } catch (DuplicateKeyException e) {
                        log.debug("预置模板并发已安装，跳过: tenantId={}, key={}", tenantId, key);
                    } catch (Exception e) {
                        log.error("预置模板安装失败（跳过继续）: tenantId={}, key={}", tenantId, key, e);
                    }
                }
            } catch (Exception e) {
                log.error("预置模板租户级安装失败（跳过继续）: tenantId={}", tenantId, e);
            } finally {
                TraceContext.clear();
            }
        }
    }

    private void seedOne(String key) throws Exception {
        FlowModel existing = latestModel(key);
        if (existing != null) {
            // 幂等：已存在跳过；历史 DRAFT（部署失败遗留）补部署
            if (DeployState.DRAFT.name().equals(existing.getDeployState())) {
                modelService.deploy(existing.getId());
                log.info("预置模板补部署: tenantId={}, key={}, modelId={}",
                        TraceContext.getTenantId(), key, existing.getId());
            }
            return;
        }
        String bpmnXml = loadXml(key);
        ModelDtos.ModelResponse created = modelService.create(
                new ModelDtos.ModelCreateRequest(key, bpmnXml, null));
        modelService.deploy(created.id());
        log.info("预置模板已安装: tenantId={}, key={}, modelId={}",
                TraceContext.getTenantId(), key, created.id());
    }

    private FlowModel latestModel(String key) {
        List<FlowModel> models = flowModelMapper.selectList(new LambdaQueryWrapper<FlowModel>()
                .eq(FlowModel::getProcDefKey, key)
                .orderByDesc(FlowModel::getVersion)
                .last("limit 1"));
        return models == null || models.isEmpty() ? null : models.get(0);
    }

    private String loadXml(String key) throws Exception {
        // MVP 既有文件为小写 generic-approval.bpmn20.xml，classpath 区分大小写
        String resource = GENERIC_KEY.equals(key) ? "generic-approval.bpmn20.xml" : key + ".bpmn20.xml";
        try (InputStream in = new ClassPathResource("processes/" + resource).getInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    public static List<String> seedKeys() {
        List<String> keys = new ArrayList<>();
        keys.add(GENERIC_KEY);
        keys.addAll(PRESET_KEYS);
        return keys;
    }

    private List<Long> parseTenants(String config) {
        List<Long> tenants = new ArrayList<>();
        if (config == null || config.isBlank()) {
            return tenants;
        }
        for (String part : config.split(",")) {
            String s = part.trim();
            if (s.isEmpty()) {
                continue;
            }
            try {
                long id = Long.parseLong(s);
                if (id > 0 && !tenants.contains(id)) {
                    tenants.add(id);
                }
            } catch (NumberFormatException e) {
                log.warn("hris.flow.seed-tenants 含非法租户项，忽略: {}", s);
            }
        }
        return tenants;
    }
}
