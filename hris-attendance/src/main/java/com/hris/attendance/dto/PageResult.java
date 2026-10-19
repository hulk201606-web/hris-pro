package com.hris.attendance.dto;

import java.util.List;

/**
 * 通用分页结果（域：考勤，分层：DTO/公共结构）。封装分页查询的记录列表与总数。
 */
public record PageResult<T>(List<T> records, long page, long size, long total) {
}
