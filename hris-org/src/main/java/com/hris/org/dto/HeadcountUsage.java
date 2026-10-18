package com.hris.org.dto;

/**
 * 编制看板行（在编/空缺/占用率）。
 */
public record HeadcountUsage(
        Long deptId,
        Long positionId,
        String period,
        Integer quota,
        Integer occupied,
        Integer vacancy,
        double usageRate) {
}
