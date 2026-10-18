package com.hris.platform.dto;

import jakarta.validation.constraints.NotBlank;

import java.time.LocalDateTime;

/**
 * 数据字典 DTO（PLT-F-007）。
 */
public final class DictDtos {

    private DictDtos() {
    }

    /** 字典类型创建/更新请求（code 为空时自动生成） */
    public record DictTypeRequest(
            @NotBlank(message = "字典类型名称不能为空") String name,
            String code) {
    }

    /** 字典类型响应 */
    public record DictTypeResponse(Long id, String name, String code, LocalDateTime updatedAt) {
    }

    /** 字典项创建/更新请求（value 为空时取 label） */
    public record DictItemRequest(
            @NotBlank(message = "字典项标签不能为空") String label,
            String value,
            Integer sort) {
    }

    /** 字典项响应 */
    public record DictItemResponse(
            Long id,
            String typeCode,
            String label,
            String value,
            Integer sort) {
    }
}
