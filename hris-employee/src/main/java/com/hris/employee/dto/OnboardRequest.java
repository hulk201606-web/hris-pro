package com.hris.employee.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.List;

/**
 * 入职单请求（EMP-F-003/004/006）。并发起 EMP_ONBOARD 流程（MVP 落单 + 预留 flow）。
 */
public record OnboardRequest(
        @NotNull(message = "empId 不能为空") Long empId,
        @NotNull(message = "legalEntityId 不能为空") Long legalEntityId,
        @NotNull(message = "deptId 不能为空") Long deptId,
        @NotNull(message = "positionId 不能为空") Long positionId,
        Long gradeId,
        @NotBlank(message = "hireType 不能为空") String hireType,
        Long supervisorEmpId,
        @NotNull(message = "hireDate 不能为空") LocalDate hireDate,
        String username,
        String password,
        List<String> roleCodes,
        String reason) {
}
