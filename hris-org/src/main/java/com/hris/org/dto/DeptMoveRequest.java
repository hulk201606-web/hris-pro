package com.hris.org.dto;

/**
 * 部门移动请求（POST /org/depts/{id}/move，ORG-F-002 深化）。
 * newParentId 为空/0 表示移动为根；sortOrder 为目标兄弟位次。
 */
public record DeptMoveRequest(Long newParentId, Integer sortOrder) {
}
