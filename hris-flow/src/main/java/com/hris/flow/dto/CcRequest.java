package com.hris.flow.dto;

import java.util.List;

/**
 * 补抄送请求（FLW-F-011）。
 */
public record CcRequest(
        /** 被抄送人用户 ID 列表 */
        List<Long> ccEmpIds) {
}
