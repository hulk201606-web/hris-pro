package com.hris.org.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 岗位/职务/职级/职级带 通用创建/更新请求（code 为空时自动生成）。
 * jobFamily 仅岗位（ORG-F-006 深化岗位序列）使用，其余目录忽略。
 */
public record CatalogRequest(
        @NotBlank(message = "名称不能为空") String name,
        String code,
        Integer status,
        Long gradeBandId,
        String jobFamily) {

    /** 兼容构造：非岗位目录（职务/职级/职级带/法人）沿用 4 参形态。 */
    public CatalogRequest(String name, String code, Integer status, Long gradeBandId) {
        this(name, code, status, gradeBandId, null);
    }
}
