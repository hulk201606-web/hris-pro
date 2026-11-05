package com.hris.employee.service;

import com.hris.common.audit.AuditRecord;
import com.hris.common.client.PlatformAuditClient;
import com.hris.employee.entity.ChangeAudit;
import com.hris.employee.mapper.ChangeAuditMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 员工字段级 diff 审计（EMP-F-020 / 批11#21）：diff 生成、敏感值脱敏、无变更短路。
 */
@ExtendWith(MockitoExtension.class)
class EmpChangeAuditServiceTest {

    @Mock
    private ChangeAuditMapper changeAuditMapper;
    @Mock
    private ObjectProvider<PlatformAuditClient> auditClientProvider;
    @Mock
    private PlatformAuditClient auditClient;

    private EmpChangeAuditService service;

    @BeforeEach
    void setUp() {
        service = new EmpChangeAuditService(changeAuditMapper, auditClientProvider);
    }

    @Test
    @DisplayName("多字段变更 → 每字段一行 emp_change_audit + 平台 detail.diff")
    void recordsFieldLevelDiff() {
        when(auditClientProvider.getIfAvailable()).thenReturn(auditClient);
        Map<String, Object> before = new LinkedHashMap<>();
        before.put("name", "张三");
        before.put("email", "old@x.com");
        Map<String, Object> after = new LinkedHashMap<>();
        after.put("name", "李四");
        after.put("email", "new@x.com");

        service.record(100L, "UPDATE", before, after, "改档");

        ArgumentCaptor<ChangeAudit> rows = ArgumentCaptor.forClass(ChangeAudit.class);
        verify(changeAuditMapper, org.mockito.Mockito.times(2)).insert(rows.capture());
        assertEquals("name", rows.getAllValues().get(0).getFieldName());
        assertEquals("张三", rows.getAllValues().get(0).getOldValue());
        assertEquals("李四", rows.getAllValues().get(0).getNewValue());

        ArgumentCaptor<AuditRecord> audit = ArgumentCaptor.forClass(AuditRecord.class);
        verify(auditClient).append(audit.capture());
        Map<String, Object> diff = (Map<String, Object>) audit.getValue().detail().get("diff");
        assertTrue(diff.containsKey("name"));
        assertTrue(diff.containsKey("email"));
    }

    @Test
    @DisplayName("证件/手机变更 → 审计只出现掩码，明文不落库")
    void masksSensitiveValues() {
        when(auditClientProvider.getIfAvailable()).thenReturn(auditClient);
        Map<String, Object> before = new LinkedHashMap<>();
        before.put("certNo", "110101199001011234");
        before.put("mobile", "13800138000");
        Map<String, Object> after = new LinkedHashMap<>();
        after.put("certNo", "110101199001015678");
        after.put("mobile", "13900139000");

        service.record(100L, "UPDATE", before, after, null);

        ArgumentCaptor<AuditRecord> audit = ArgumentCaptor.forClass(AuditRecord.class);
        verify(auditClient).append(audit.capture());
        String detail = String.valueOf(audit.getValue().detail());
        assertTrue(detail.contains("1101********78"), "证件号应掩码: " + detail);
        assertTrue(detail.contains("139****9000"), "手机号应掩码: " + detail);
        assertFalse(detail.contains("110101199001015678"), "明文证件号不得入审计");
        assertFalse(detail.contains("13900139000"), "明文手机号不得入审计");
    }

    @Test
    @DisplayName("无变更 → 不落 emp_change_audit，也不调平台审计")
    void skipsWhenNoChange() {
        Map<String, Object> snapshot = Map.of("name", "张三", "email", "a@x.com");

        service.record(100L, "UPDATE", snapshot, snapshot, null);

        verify(changeAuditMapper, never()).insert(any(ChangeAudit.class));
        verify(auditClientProvider, never()).getIfAvailable();
    }

    @Test
    @DisplayName("平台审计不可用 → 业务审计已落库，异常不外溢")
    void platformFailureDoesNotThrow() {
        when(auditClientProvider.getIfAvailable()).thenReturn(auditClient);
        org.mockito.Mockito.doThrow(new RuntimeException("plt down"))
                .when(auditClient).append(any(AuditRecord.class));

        service.record(100L, "UPDATE", Map.of("name", "张三"), Map.of("name", "李四"), null);

        verify(changeAuditMapper).insert(any(ChangeAudit.class));
    }
}
