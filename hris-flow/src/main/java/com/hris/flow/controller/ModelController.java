package com.hris.flow.controller;

import com.hris.common.api.R;
import com.hris.common.security.RequirePerm;
import com.hris.flow.dto.ModelDtos;
import com.hris.flow.service.ModelService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
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
}
