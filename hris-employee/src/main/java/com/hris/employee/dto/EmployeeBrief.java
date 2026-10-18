package com.hris.employee.dto;

/**
 * 花名册行（EMP-F-022 多维筛选结果，默认脱敏展示）。
 */
public record EmployeeBrief(
        Long id, String empNo, String name, String gender,
        Integer status, String statusLabel, Integer blacklisted,
        String certNoMask, String mobileMask, String email,
        Long deptId, Long positionId, String hireType, Long supervisorEmpId) {
}
