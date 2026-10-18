package com.hris.org.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * 部门树节点（树查询与历史快照统一返回结构）。
 */
public record DeptNode(
        Long id,
        Long parentId,
        String name,
        String code,
        String path,
        Long managerEmpId,
        LocalDate effectiveFrom,
        LocalDate effectiveTo,
        Integer status,
        List<DeptNode> children) {

    public static DeptNode leaf(Long id, Long parentId, String name, String code, String path,
                                Long managerEmpId, LocalDate effectiveFrom, LocalDate effectiveTo,
                                Integer status) {
        return new DeptNode(id, parentId, name, code, path, managerEmpId,
                effectiveFrom, effectiveTo, status, List.of());
    }
}
