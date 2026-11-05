package com.hris.flow.service;

import com.hris.common.exception.BizException;
import com.hris.common.exception.ErrorCode;
import com.hris.common.trace.TraceContext;
import com.hris.flow.dto.ModelDtos.ModelResponse;
import com.hris.flow.dto.ModelDtos.ModelValidateResponse;
import com.hris.flow.entity.FlowModel;
import com.hris.flow.enums.DeployState;
import com.hris.flow.mapper.FlowModelMapper;
import org.flowable.engine.RepositoryService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * ModelService 设计器支撑单测（批 12 #24）：XML 往返、非法 XML 校验、保存 version+1。
 */
@ExtendWith(MockitoExtension.class)
class ModelServiceXmlTest {

    /** 合法最小流程：开始 → 用户任务 → 结束。 */
    private static final String VALID_XML = """
            <?xml version="1.0" encoding="UTF-8"?>
            <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
                         xmlns:flowable="http://flowable.org/bpmn"
                         targetNamespace="http://hris.com/flow">
              <process id="leave_apply" name="请假申请" isExecutable="true">
                <startEvent id="start" name="开始"/>
                <sequenceFlow id="f1" sourceRef="start" targetRef="approve"/>
                <userTask id="approve" name="主管审批" flowable:assignee="${managerId}"/>
                <sequenceFlow id="f2" sourceRef="approve" targetRef="end"/>
                <endEvent id="end" name="结束"/>
              </process>
            </definitions>
            """;

    @Mock
    private FlowModelMapper mapper;
    @Mock
    private RepositoryService repositoryService;

    private ModelService service;

    @BeforeEach
    void setUp() {
        TraceContext.setTenantId(100001L);
        service = new ModelService(mapper, repositoryService, new BpmnXmlValidator());
    }

    @AfterEach
    void tearDown() {
        TraceContext.setTenantId(null);
    }

    private FlowModel model(long id, int version, String xml) {
        FlowModel m = new FlowModel();
        m.setId(id);
        m.setTenantId(100001L);
        m.setProcDefKey("leave_apply");
        m.setVersion(version);
        m.setBpmnXml(xml);
        m.setFormSchemaJson("{\"fields\":[]}");
        m.setDeployState(DeployState.DRAFT.name());
        return m;
    }

    @Nested
    @DisplayName("XML 读取与保存")
    class XmlRoundTrip {

        @Test
        @DisplayName("读取 → 原样返回库中 XML（设计器 importXML 可直接解析）")
        void returnsStoredXml() {
            when(mapper.selectById(1L)).thenReturn(model(1L, 1, VALID_XML));

            assertEquals(VALID_XML, service.xml(1L));
        }

        @Test
        @DisplayName("保存 → 同 key 新版本（version+1，DRAFT），XML 原样落库")
        void savesAsNextVersion() {
            when(mapper.selectById(1L)).thenReturn(model(1L, 2, VALID_XML));
            when(mapper.selectOne(any())).thenReturn(model(1L, 2, VALID_XML));

            ModelResponse resp = service.saveXml(1L, VALID_XML);

            assertEquals(3, resp.version(), "版本号在最新版本基础上 +1");
            assertEquals("leave_apply", resp.procDefKey());
            assertEquals(DeployState.DRAFT.name(), resp.deployState(), "保存不自动部署");
            ArgumentCaptor<FlowModel> captor = ArgumentCaptor.forClass(FlowModel.class);
            verify(mapper).insert(captor.capture());
            assertEquals(VALID_XML, captor.getValue().getBpmnXml());
            assertEquals("{\"fields\":[]}", captor.getValue().getFormSchemaJson(), "表单 schema 继承");
        }

        @Test
        @DisplayName("模型不存在 → FLOW_NOT_FOUND")
        void rejectsMissingModel() {
            when(mapper.selectById(404L)).thenReturn(null);

            BizException e = assertThrows(BizException.class, () -> service.xml(404L));

            assertEquals(ErrorCode.FLOW_NOT_FOUND.getCode(), e.getCode());
        }
    }

    @Nested
    @DisplayName("XML 校验（不部署、不落库）")
    class ValidateXml {

        @BeforeEach
        void existingModel() {
            when(mapper.selectById(1L)).thenReturn(model(1L, 1, VALID_XML));
        }

        @Test
        @DisplayName("合法 BPMN → valid=true 且无错误")
        void acceptsValidBpmn() {
            ModelValidateResponse resp = service.validateXml(1L, VALID_XML);

            assertTrue(resp.valid());
            assertTrue(resp.errors().isEmpty());
        }

        @Test
        @DisplayName("非法 XML（标签未闭合）→ valid=false 且返回解析错误")
        void rejectsMalformedXml() {
            ModelValidateResponse resp = service.validateXml(1L, "<definitions><process></definitions>");

            assertFalse(resp.valid());
            assertFalse(resp.errors().isEmpty());
        }

        @Test
        @DisplayName("空 XML → valid=false")
        void rejectsBlankXml() {
            assertFalse(service.validateXml(1L, "  ").valid());
        }

        @Test
        @DisplayName("结构非法（连线指向不存在的节点）→ valid=false")
        void rejectsStructurallyInvalidProcess() {
            String xml = VALID_XML.replace("<endEvent id=\"end\" name=\"结束\"/>", "");

            ModelValidateResponse resp = service.validateXml(1L, xml);

            assertFalse(resp.valid());
            assertFalse(resp.errors().isEmpty());
        }
    }
}
