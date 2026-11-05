package com.hris.flow.controller;

import com.hris.common.api.R;
import com.hris.common.security.RequirePerm;
import com.hris.flow.dto.ModelDtos;
import com.hris.flow.service.ModelService;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 流程模型端点（草稿/版本/部署）。
 */
@RestController
@RequestMapping("/api/v1/flow/models")
public class ModelController {

    private final ModelService modelService;

    public ModelController(ModelService modelService) {
        this.modelService = modelService;
    }

    @RequirePerm("flow:model:read")
    @GetMapping
    public R<ModelDtos.ModelPageResult> list(@RequestParam(defaultValue = "1") long page,
                                             @RequestParam(defaultValue = "20") long size) {
        return R.ok(modelService.list(page, size));
    }

    @RequirePerm("flow:model:write")
    @PostMapping
    public R<ModelDtos.ModelResponse> create(@Valid @RequestBody ModelDtos.ModelCreateRequest req) {
        return R.ok(modelService.create(req));
    }

    @RequirePerm("flow:model:write")
    @PostMapping("/{id}/deploy")
    public R<ModelDtos.DeployResponse> deploy(@PathVariable("id") Long id) {
        return R.ok(modelService.deploy(id));
    }

    @RequirePerm("flow:model:read")
    @GetMapping("/{id}/versions")
    public R<List<ModelDtos.ModelVersionResponse>> versions(@PathVariable("id") Long id) {
        return R.ok(modelService.versions(id));
    }

    /**
     * 读取模型 BPMN XML（批 12 #24，bpmn-js 设计器 importXML 用）。
     * 直接返回 text/xml 原文，前端取 responseText 即可，无需解包 R 信封。
     */
    @RequirePerm("flow:model:read")
    @GetMapping(value = "/{id}/xml", produces = MediaType.APPLICATION_XML_VALUE)
    public ResponseEntity<String> xml(@PathVariable("id") Long id) {
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_XML)
                .body(modelService.xml(id));
    }

    /**
     * 校验 BPMN XML（不部署、不落库）：解析 + Flowable 流程校验，返回 {valid, errors[]}。
     */
    @RequirePerm("flow:model:write")
    @PostMapping("/{id}/validate-xml")
    public R<ModelDtos.ModelValidateResponse> validateXml(@PathVariable("id") Long id,
                                                          @Valid @RequestBody ModelDtos.ModelXmlRequest req) {
        return R.ok(modelService.validateXml(id, req.xml()));
    }

    /**
     * 保存设计器 XML：同 key 生成新版本（version+1，DRAFT），不自动部署。
     * 返回新版本模型，前端后续保存/部署须使用返回的 id。
     */
    @RequirePerm("flow:model:write")
    @PutMapping("/{id}/xml")
    public R<ModelDtos.ModelResponse> saveXml(@PathVariable("id") Long id,
                                              @Valid @RequestBody ModelDtos.ModelXmlRequest req) {
        return R.ok(modelService.saveXml(id, req.xml()));
    }
}
