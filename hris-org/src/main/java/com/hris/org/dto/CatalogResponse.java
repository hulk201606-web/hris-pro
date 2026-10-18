package com.hris.org.dto;

/**
 * 岗位/职务/职级/职级带 通用响应。
 */
public record CatalogResponse(Long id, String name, String code, Integer status, Long gradeBandId) {
}
