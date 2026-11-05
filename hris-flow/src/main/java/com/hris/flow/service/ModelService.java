package com.hris.flow.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hris.common.exception.BizException;
import com.hris.common.exception.ErrorCode;
import com.hris.common.trace.TraceContext;
import com.hris.flow.dto.ModelDtos;
import com.hris.flow.entity.FlowModel;
import com.hris.flow.enums.DeployState;
import com.hris.flow.mapper.FlowModelMapper;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.repository.Deployment;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 流程模型服务（FLW-F-001/002）：草稿/版本/部署。
 *
 * <p>设计态存 flow_ext_model（bpmn_xml + form_schema_json），部署态由引擎 ACT_RE_* 管理。
 * 同 key 多版本：在跑实例按旧版继续、新发起走最新版（docs/03-07 §2）。</p>
 */
@Service
public class ModelService {

    private final FlowModelMapper mapper;
    private final RepositoryService repositoryService;
    private final BpmnXmlValidator bpmnXmlValidator;

    public ModelService(FlowModelMapper mapper, RepositoryService repositoryService,
                        BpmnXmlValidator bpmnXmlValidator) {
        this.mapper = mapper;
        this.repositoryService = repositoryService;
        this.bpmnXmlValidator = bpmnXmlValidator;
    }

    public ModelDtos.ModelPageResult list(long page, long size) {
        Long tenantId = requireTenant();
        List<FlowModel> models = mapper.selectList(new LambdaQueryWrapper<FlowModel>()
                .eq(FlowModel::getTenantId, tenantId)
                .orderByAsc(FlowModel::getProcDefKey)
                .orderByDesc(FlowModel::getVersion)
                .last("limit " + offset(page, size) + ", " + safeSize(size)));
        long total = mapper.selectCount(new LambdaQueryWrapper<FlowModel>()
                .eq(FlowModel::getTenantId, tenantId));
        List<ModelDtos.ModelResponse> records = models.stream()
                .map(m -> new ModelDtos.ModelResponse(m.getId(), m.getProcDefKey(), m.getVersion(),
                        m.getDeployState(), m.getCreatedAt()))
                .toList();
        return new ModelDtos.ModelPageResult(records, page, size, total);
    }

    /** 创建模型草稿（同 key 版本递增）。tenant_id 由租户插件自动注入（禁手写赋值）。 */
    @Transactional(rollbackFor = Exception.class)
    public ModelDtos.ModelResponse create(ModelDtos.ModelCreateRequest req) {
        requireTenant();
        FlowModel model = new FlowModel();
        model.setProcDefKey(req.procDefKey());
        model.setVersion(nextVersion(req.procDefKey()));
        model.setBpmnXml(req.bpmnXml());
        model.setFormSchemaJson(req.formSchemaJson());
        model.setDeployState(DeployState.DRAFT.name());
        mapper.insert(model);
        return new ModelDtos.ModelResponse(model.getId(), model.getProcDefKey(), model.getVersion(),
                model.getDeployState(), model.getCreatedAt());
    }

    /** 部署指定版本模型到引擎。 */
    @Transactional(rollbackFor = Exception.class)
    public ModelDtos.DeployResponse deploy(Long id) {
        Long tenantId = requireTenant();
        FlowModel model = requireModel(id);
        Deployment deployment = repositoryService.createDeployment()
                .addString(model.getProcDefKey() + ".bpmn20.xml", model.getBpmnXml())
                .name(model.getProcDefKey() + "-v" + model.getVersion())
                .tenantId(String.valueOf(tenantId))
                .deploy();
        model.setDeployState(DeployState.DEPLOYED.name());
        mapper.updateById(model);
        return new ModelDtos.DeployResponse(model.getId(), model.getProcDefKey(), model.getVersion(),
                deployment.getId());
    }

    /** 读取模型 BPMN XML（设计器 importXML 用）。 */
    public String xml(Long id) {
        requireTenant();
        return requireModel(id).getBpmnXml();
    }

    /** 校验 BPMN XML（不部署、不落库；解析 + Flowable 流程校验）。模型不存在时抛 FLOW_NOT_FOUND。 */
    public ModelDtos.ModelValidateResponse validateXml(Long id, String xml) {
        requireTenant();
        requireModel(id);
        List<String> errors = bpmnXmlValidator.validate(xml);
        return new ModelDtos.ModelValidateResponse(errors.isEmpty(), errors);
    }

    /**
     * 保存设计器 XML：同 key 生成新版本（version+1，状态 DRAFT），旧版本行保留可追溯。
     * 不自动部署——部署仍走 POST /models/{id}/deploy。
     *
     * @return 新版本模型（前端需以返回的 id 继续保存/部署）
     */
    @Transactional(rollbackFor = Exception.class)
    public ModelDtos.ModelResponse saveXml(Long id, String xml) {
        requireTenant();
        FlowModel source = requireModel(id);
        FlowModel model = new FlowModel();
        model.setProcDefKey(source.getProcDefKey());
        model.setVersion(nextVersion(source.getProcDefKey()));
        model.setBpmnXml(xml);
        model.setFormSchemaJson(source.getFormSchemaJson());
        model.setDeployState(DeployState.DRAFT.name());
        mapper.insert(model);
        return new ModelDtos.ModelResponse(model.getId(), model.getProcDefKey(), model.getVersion(),
                model.getDeployState(), model.getCreatedAt());
    }

    /** 查询某模型 key 的全部版本。 */
    public List<ModelDtos.ModelVersionResponse> versions(Long id) {
        Long tenantId = requireTenant();
        FlowModel model = requireModel(id);
        List<FlowModel> list = mapper.selectList(new LambdaQueryWrapper<FlowModel>()
                .eq(FlowModel::getTenantId, tenantId)
                .eq(FlowModel::getProcDefKey, model.getProcDefKey())
                .orderByDesc(FlowModel::getVersion));
        return list.stream()
                .map(m -> new ModelDtos.ModelVersionResponse(m.getId(), m.getVersion(),
                        m.getDeployState(), m.getCreatedAt()))
                .toList();
    }

    private FlowModel requireModel(Long id) {
        FlowModel model = mapper.selectById(id);
        if (model == null) {
            throw new BizException(ErrorCode.FLOW_NOT_FOUND, "模型不存在");
        }
        return model;
    }

    private int nextVersion(String procDefKey) {
        FlowModel latest = mapper.selectOne(new LambdaQueryWrapper<FlowModel>()
                .eq(FlowModel::getTenantId, requireTenant())
                .eq(FlowModel::getProcDefKey, procDefKey)
                .orderByDesc(FlowModel::getVersion)
                .last("limit 1"));
        return latest == null ? 1 : latest.getVersion() + 1;
    }

    private long requireTenant() {
        Long tenantId = TraceContext.getTenantId();
        if (tenantId == null || tenantId == 0L) {
            throw new BizException(ErrorCode.TENANT_INVALID);
        }
        return tenantId;
    }

    private int offset(long page, long size) {
        long p = Math.max(1, page);
        return (int) ((p - 1) * Math.max(1, size));
    }

    private int safeSize(long size) {
        return (int) Math.min(Math.max(1, size), 200);
    }
}
