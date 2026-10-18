package com.hris.employee.dto;

import java.util.List;

/**
 * 分页结果（docs/07 §1：data={records[], page, size, total}）。
 */
public record PageResult<T>(List<T> records, long page, long size, long total) {
}
