package com.hris.performance.service;

import com.hris.common.exception.BizException;
import com.hris.common.exception.ErrorCode;
import com.hris.performance.dto.AppealDtos.AppealCreateRequest;
import com.hris.performance.dto.AppealDtos.AppealResponse;
import com.hris.performance.dto.AppealDtos.AppealUpdateRequest;
import com.hris.performance.entity.Appeal;
import com.hris.performance.mapper.AppealMapper;
import com.hris.performance.mapper.CycleMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * AppealService 单元测试（docs/19 §9 遗留项：PRF_APPEAL 审批发起接入）：
 * biz_key 服务端生成与唯一冲突重试、发起成功回写 proc_inst_id、fail-open、终态守卫。
 */
@ExtendWith(MockitoExtension.class)
class AppealServiceTest {

    @Mock
    private AppealMapper mapper;

    @Mock
    private FlowIntegration flowIntegration;

    @Mock
    private CycleMapper cycleMapper;

    private AppealService service;

    @BeforeEach
    void setUp() {
        service = new AppealService(mapper, flowIntegration, cycleMapper);
    }

    private AppealCreateRequest createReq() {
        return new AppealCreateRequest(200001L, 1L, "对考核结果 A 评级有异议");
    }

    private Appeal existing(String status) {
        Appeal e = new Appeal();
        e.setId(5L);
        e.setEmpId(200001L);
        e.setCycleId(1L);
        e.setBizKey("prf_appeal:1001");
        e.setReason("原由");
        e.setStatus(status);
        e.setProcInstId("pi-1");
        return e;
    }

    @Nested
    @DisplayName("创建：biz_key 服务端生成 + 发起流程")
    class Create {

        @Test
        @DisplayName("创建即提交：status 强制 PROCESSING，biz_key 服务端生成且与流程 bizKey 一致，proc_inst_id 回写")
        void createStartsFlowAndWritesBackProcInstId() {
            when(mapper.insert(any(Appeal.class))).thenReturn(1);
            when(flowIntegration.start(eq(AppealService.BIZ_TYPE_APPEAL), anyString(), any())).thenReturn("pi-9001");

            AppealResponse resp = service.create(createReq());

            assertEquals(AppealService.STATUS_PROCESSING, resp.status());
            assertNotNull(resp.bizKey());
            assertTrue(resp.bizKey().startsWith("prf_appeal:"), "biz_key 应为服务端生成前缀格式: " + resp.bizKey());
            assertEquals("pi-9001", resp.procInstId());
            verify(mapper).updateById(any(Appeal.class));
            verify(flowIntegration).start(eq(AppealService.BIZ_TYPE_APPEAL), eq(resp.bizKey()), any());
        }

        @Test
        @DisplayName("payload 非敏感摘要：含 empId/cycleId，reason 超长截断")
        void payloadContainsNonSensitiveSummary() {
            when(mapper.insert(any(Appeal.class))).thenReturn(1);
            when(flowIntegration.start(anyString(), anyString(), any())).thenReturn("pi-1");

            AppealCreateRequest longReason = new AppealCreateRequest(200001L, 1L, "理".repeat(300));
            service.create(longReason);

            @SuppressWarnings("unchecked")
            ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
            verify(flowIntegration).start(anyString(), anyString(), captor.capture());
            Map<String, Object> payload = captor.getValue();
            assertEquals(200001L, payload.get("empId"));
            assertEquals(1L, payload.get("cycleId"));
            String summary = String.valueOf(payload.get("reasonSummary"));
            assertTrue(summary.length() <= 104, "reason 摘要应被截断: " + summary.length());
        }

        @Test
        @DisplayName("biz_key 唯一冲突：重新生成重试，两次单号不同")
        void bizKeyConflictRetriesWithNewKey() {
            // 同一实体实例在重试间被复用，须在 insert 时点捕获值而非实体引用
            List<String> seenKeys = new ArrayList<>();
            when(mapper.insert(any(Appeal.class))).thenAnswer(inv -> {
                seenKeys.add(((Appeal) inv.getArgument(0)).getBizKey());
                if (seenKeys.size() == 1) {
                    throw new DataIntegrityViolationException("uk_prf_appeal_biz");
                }
                return 1;
            });
            when(flowIntegration.start(anyString(), anyString(), any())).thenReturn("pi-2");

            AppealResponse resp = service.create(createReq());

            assertEquals(2, seenKeys.size(), "冲突后应重新落库");
            assertNotEquals(seenKeys.get(0), seenKeys.get(1), "冲突后应重新生成 biz_key");
            assertTrue(resp.bizKey().startsWith("prf_appeal:"));
            assertEquals(seenKeys.get(1), resp.bizKey());
        }

        @Test
        @DisplayName("biz_key 冲突耗尽重试次数 → PRF_ERROR")
        void bizKeyConflictExhausted() {
            when(mapper.insert(any(Appeal.class)))
                    .thenThrow(new DataIntegrityViolationException("uk_prf_appeal_biz"));

            BizException e = assertThrows(BizException.class, () -> service.create(createReq()));

            assertEquals(ErrorCode.PRF_ERROR.getCode(), e.getCode());
            verify(mapper, times(3)).insert(any(Appeal.class));
            verify(flowIntegration, never()).start(anyString(), anyString(), any());
        }
    }

    @Nested
    @DisplayName("fail-open：flow 不可用降级落单")
    class FailOpen {

        @Test
        @DisplayName("flow 不可用（返回 null）→ 单据保留 PROCESSING、proc_inst_id 置空、不阻断落库")
        void keepsProcessingWithNullProcInstId() {
            when(mapper.insert(any(Appeal.class))).thenReturn(1);
            when(flowIntegration.start(anyString(), anyString(), any())).thenReturn(null);

            AppealResponse resp = service.create(createReq());

            assertEquals(AppealService.STATUS_PROCESSING, resp.status());
            assertNull(resp.procInstId());
            verify(mapper).insert(any(Appeal.class));
            verify(mapper, never()).updateById(any(Appeal.class));
        }
    }

    @Nested
    @DisplayName("更新：旁路清理 + 终态守卫")
    class Update {

        @Test
        @DisplayName("终态 APPROVED 不可修改，不再重发流程")
        void approvedIsFrozen() {
            when(mapper.selectById(5L)).thenReturn(existing("APPROVED"));

            BizException e = assertThrows(BizException.class,
                    () -> service.update(5L, new AppealUpdateRequest(1L, 1L, "改理由")));

            assertEquals(ErrorCode.PRF_ERROR.getCode(), e.getCode());
            verify(mapper, never()).updateById(any(Appeal.class));
            verify(flowIntegration, never()).start(anyString(), anyString(), any());
        }

        @Test
        @DisplayName("终态 REJECTED 不可修改，不再重发流程")
        void rejectedIsFrozen() {
            when(mapper.selectById(5L)).thenReturn(existing("REJECTED"));

            assertThrows(BizException.class,
                    () -> service.update(5L, new AppealUpdateRequest(1L, 1L, "改理由")));

            verify(mapper, never()).updateById(any(Appeal.class));
            verify(flowIntegration, never()).start(anyString(), anyString(), any());
        }

        @Test
        @DisplayName("PROCESSING 更新仅改基础字段：biz_key/status/proc_inst_id 不被触碰，也不重发流程")
        void updateOnlyTouchesBasicFields() {
            Appeal origin = existing("PROCESSING");
            when(mapper.selectById(5L)).thenReturn(origin);
            when(mapper.updateById(any(Appeal.class))).thenReturn(1);

            AppealResponse resp = service.update(5L, new AppealUpdateRequest(200002L, 2L, "新理由"));

            assertEquals("prf_appeal:1001", resp.bizKey());
            assertEquals("PROCESSING", resp.status());
            assertEquals("pi-1", resp.procInstId());
            assertEquals("新理由", resp.reason());
            verify(flowIntegration, never()).start(anyString(), anyString(), any());
        }
    }
}
