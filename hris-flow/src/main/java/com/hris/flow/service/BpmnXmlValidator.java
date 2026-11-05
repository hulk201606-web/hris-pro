package com.hris.flow.service;

import org.flowable.bpmn.converter.BpmnXMLConverter;
import org.flowable.bpmn.model.BpmnModel;
import org.flowable.common.engine.impl.util.io.StringStreamSource;
import org.flowable.validation.ProcessValidator;
import org.flowable.validation.ProcessValidatorFactory;
import org.flowable.validation.ValidationError;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * BPMN XML 校验（批 12 #24，流程设计器后端支撑）。
 *
 * <p>选型：不试部署（会在 ACT_RE_* 落真实部署记录、污染引擎），改为「XML 解析 + Flowable
 * 流程校验器」两步：{@link BpmnXMLConverter#convertToBpmnModel} 解析（含 XSD 校验），
 * 再用 {@link ProcessValidator} 做结构/语义校验（必填属性、连线、网关等）。</p>
 */
@Component
public class BpmnXmlValidator {

    private static final Logger log = LoggerFactory.getLogger(BpmnXmlValidator.class);

    /** 单次校验返回的错误上限，避免病态 XML 刷爆响应体。 */
    private static final int MAX_ERRORS = 50;

    private final ProcessValidator validator = new ProcessValidatorFactory().createDefaultProcessValidator();

    /**
     * 校验 BPMN XML。
     *
     * @return 错误列表，空列表表示通过
     */
    public List<String> validate(String xml) {
        List<String> errors = new ArrayList<>();
        if (xml == null || xml.isBlank()) {
            errors.add("BPMN XML 为空");
            return errors;
        }
        BpmnModel model;
        try {
            model = new BpmnXMLConverter()
                    .convertToBpmnModel(new StringStreamSource(xml), true, false);
        } catch (Exception e) {
            log.debug("BPMN XML 解析失败: {}", e.getMessage());
            errors.add("XML 解析失败: " + rootMessage(e));
            return errors;
        }
        if (model == null) {
            errors.add("XML 解析失败: 无法识别为 BPMN 2.0 模型");
            return errors;
        }
        for (ValidationError error : validator.validate(model)) {
            if (errors.size() >= MAX_ERRORS) {
                errors.add("错误过多，仅显示前 " + MAX_ERRORS + " 条");
                break;
            }
            errors.add(format(error));
        }
        return errors;
    }

    private String format(ValidationError error) {
        StringBuilder sb = new StringBuilder();
        if (error.getActivityId() != null && !error.getActivityId().isBlank()) {
            sb.append('[').append(error.getActivityId()).append("] ");
        }
        sb.append(error.getDefaultDescription());
        if (error.isWarning()) {
            sb.append("（警告）");
        }
        return sb.toString();
    }

    private String rootMessage(Throwable e) {
        Throwable root = e;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        return root.getMessage() == null ? root.getClass().getSimpleName() : root.getMessage();
    }
}
