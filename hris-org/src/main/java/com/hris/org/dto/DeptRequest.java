package com.hris.org.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

/**
 * 部门创建/更新请求（版本化生效：effective_from 必填，未来日期为草稿）。
 */
public record DeptRequest(
        @NotBlank(message = "部门名称不能为空") String name,
        String code,
        Long parentId,
        Long managerEmpId,
        @NotNull(message = "生效开始日不能为空") LocalDate effectiveFrom,
        LocalDate effectiveTo,
        Integer status) {
}
