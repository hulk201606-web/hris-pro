package com.hris.flow.dto;

import jakarta.validation.constraints.NotBlank;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 流程模型端点 DTO（FLW-F-001/002）。
 */
public final class ModelDtos {

    private ModelDtos() {
    }

    /** 创建模型草稿请求。 */
    public record ModelCreateRequest(
            @NotBlank(message = "procDefKey 不能为空") String procDefKey,
            @NotBlank(message = "bpmnXml 不能为空") String bpmnXml,
            String formSchemaJson) {
    }

    /** 模型列表项/详情响应。 */
    public record ModelResponse(
            Long id,
            String procDefKey,
            Integer version,
            String deployState,
            LocalDateTime createdAt) {
    }

    /** 模型版本列表项。 */
    public record ModelVersionResponse(
            Long id,
            Integer version,
            String deployState,
            LocalDateTime createdAt) {
    }

    /** 部署结果。 */
    public record DeployResponse(
            Long modelId,
            String procDefKey,
            Integer version,
            String deploymentId) {
    }

    public record ModelPageResult(List<ModelResponse> records, long page, long size, long total) {
    }
}
