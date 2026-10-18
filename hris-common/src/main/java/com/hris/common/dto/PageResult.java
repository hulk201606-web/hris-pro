package com.hris.common.dto;

import java.util.List;

/**
 * 分页结果（docs/07 §1：data={records[], page, size, total}）。
 *
 * <p>各业务模块统一复用，避免在每个模块重复定义。</p>
 */
public record PageResult<T>(List<T> records, long page, long size, long total) {
}
