package com.hris.org.dto;

/**
 * 岗位/职务/职级/职级带 通用响应。jobFamily 仅岗位返回（ORG-F-006 深化岗位序列），其余为 null。
 */
public record CatalogResponse(Long id, String name, String code, Integer status, Long gradeBandId,
                              String jobFamily) {
}
