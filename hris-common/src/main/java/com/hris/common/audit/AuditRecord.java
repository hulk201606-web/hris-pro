package com.hris.common.audit;

import java.util.Map;

/**
 * 审计落库记录（docs/19 §8.7-2）。切面从 TraceContext + @AuditLog 注解组装后交给 {@link AuditSink}。
 *
 * <p>命名说明：common 中 AuditLog 已被审计注解占用，故落库记录命名为 AuditRecord；
 * 字段与 platform 的 sys_audit_log 列对齐（action=操作描述、module=业务模块），
 * ok/costMs/traceId 收敛进 detail JSON，避免宽表。</p>
 */
public record AuditRecord(
        Long tenantId,
        Long userId,
        String module,
        String action,
        boolean ok,
        long costMs,
        String ip,
        Map<String, Object> detail) {

    /** detail 兜底：空则返回不可变空 Map。 */
    public Map<String, Object> detailOrEmpty() {
        return detail == null ? Map.of() : detail;
    }
}
