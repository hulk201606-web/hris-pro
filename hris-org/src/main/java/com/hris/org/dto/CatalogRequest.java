package com.hris.org.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 岗位/职务/职级/职级带 通用创建/更新请求（code 为空时自动生成）。
 */
public record CatalogRequest(
        @NotBlank(message = "名称不能为空") String name,
        String code,
        Integer status,
        Long gradeBandId) {
}
