package com.hris.employee.dto;

/**
 * 黑名单校验 DTO（域：员工，分层：DTO/接口契约，RCS-F-011 招聘域契约）。
 */
public final class BlacklistCheckDtos {

    private BlacklistCheckDtos() {
    }

    /**
     * 校验响应。
     *
     * @param blacklisted 命中 blacklisted=1 的员工即 true（同租户内）
     * @param reason      命中原因：取该员工 emp_status_log 最近一条原因；
     *                    无流水时为固定文案。未命中为 null。
     */
    public record BlacklistCheckResponse(boolean blacklisted, String reason) {
    }
}
