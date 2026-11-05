package com.hris.employee.service;

import com.hris.common.audit.AuditRecord;
import com.hris.common.client.PlatformAuditClient;
import com.hris.common.trace.TraceContext;
import com.hris.employee.entity.ChangeAudit;
import com.hris.employee.mapper.ChangeAuditMapper;
import com.hris.employee.util.EmpDiffUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 员工字段变更审计（EMP-F-020 / 批11#21）。
 *
 * <p>统一采集入口：{@code update}/{@code rehire}/{@code transfer}/关键信息变更审批回调
 * 都调用本服务，避免各入口各写一套。落库双写：</p>
 * <ol>
 *   <li>emp_change_audit 按字段一行（兼容既有查询与前端轨迹）；</li>
 *   <li>platform sys_audit_log detail 写字段级 diff JSON
 *       {@code {field:{before,after}}}，敏感值仅掩码。</li>
 * </ol>
 *
 * <p>platform 审计经 {@link PlatformAuditClient} fail-open：审计不可用不能阻断档案变更。</p>
 */
@Service
public class EmpChangeAuditService {

    private static final Logger log = LoggerFactory.getLogger(EmpChangeAuditService.class);

    private final ChangeAuditMapper changeAuditMapper;
    private final ObjectProvider<PlatformAuditClient> auditClient;

    public EmpChangeAuditService(ChangeAuditMapper changeAuditMapper,
                                 ObjectProvider<PlatformAuditClient> auditClient) {
        this.changeAuditMapper = changeAuditMapper;
        this.auditClient = auditClient;
    }

    /**
     * 记录一次变更。
     *
     * @param empId  员工 ID
     * @param action 动作标识（CREATE/UPDATE/TRANSFER/PROFILE_CHANGE_APPROVED 等）
     * @param before 变更前字段（null=新建）
     * @param after  变更后字段（null=未提供）
     * @param reason 变更原因（可空）
     */
    public void record(Long empId, String action, Map<String, Object> before,
                       Map<String, Object> after, String reason) {
        Map<String, Object> diff = EmpDiffUtil.diff(before, after);
        if (diff.isEmpty()) {
            return;
        }
        for (Map.Entry<String, Object> e : diff.entrySet()) {
            writeChangeAudit(empId, e.getKey(), e.getValue());
        }
        appendPlatformAudit(empId, action, diff, reason);
    }

    @SuppressWarnings("unchecked")
    private void writeChangeAudit(Long empId, String field, Object change) {
        if (!(change instanceof Map<?, ?> map)) {
            return;
        }
        ChangeAudit row = new ChangeAudit();
        row.setEmpId(empId);
        row.setFieldName(field);
        row.setOldValue(toText(map.get("before")));
        row.setNewValue(toText(map.get("after")));
        changeAuditMapper.insert(row);
    }

    private void appendPlatformAudit(Long empId, String action, Map<String, Object> diff, String reason) {
        try {
            PlatformAuditClient client = auditClient.getIfAvailable();
            if (client == null) {
                return;
            }
            Map<String, Object> detail = new LinkedHashMap<>();
            detail.put("entity", "emp_employee");
            detail.put("empId", empId);
            detail.put("action", action);
            detail.put("diff", diff);
            if (reason != null && !reason.isBlank()) {
                detail.put("reason", reason);
            }
            client.append(new AuditRecord(TraceContext.getTenantId(), TraceContext.getUserId(),
                    "emp", action, true, 0, null, detail));
        } catch (Exception e) {
            log.warn("员工变更审计写入 platform 失败（不影响业务）: empId={}, action={}, err={}",
                    empId, action, e.getMessage());
        }
    }

    private String toText(Object v) {
        return v == null ? null : String.valueOf(v);
    }
}
