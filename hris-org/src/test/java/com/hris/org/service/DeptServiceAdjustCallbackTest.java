package com.hris.org.service;

import com.hris.common.exception.BizException;
import com.hris.common.exception.ErrorCode;
import com.hris.org.dto.AdjustRequestCreate;
import com.hris.org.dto.ApprovalDtos.ApprovalCallbackResponse;
import com.hris.org.dto.DeptRequest;
import com.hris.org.entity.OrgAdjustmentSnapshot;
import com.hris.org.entity.OrgDept;
import com.hris.org.mapper.OrgAdjustmentSnapshotMapper;
import com.hris.org.mapper.OrgDeptHistoryMapper;
import com.hris.org.mapper.OrgDeptMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * DeptService.handleAdjustCallback 单元测试（docs/19 §8.7-1 审批回调闭环）：
 * ORG_ADJUST 申请单状态更新幂等 + 结构化变更动作应用。
 * B-7 根治后申请单以 org_adjustment_snapshot 独立表承载。
 */
@ExtendWith(MockitoExtension.class)
class DeptServiceAdjustCallbackTest {

    @Mock
    private OrgDeptMapper deptMapper;
    @Mock
    private OrgDeptHistoryMapper deptHistoryMapper;
    @Mock
    private OrgAdjustmentSnapshotMapper adjustmentSnapshotMapper;
    @Mock
    private FlowIntegration flowIntegration;

    private DeptService service;

    @BeforeEach
    void setUp() {
        service = new DeptService(deptMapper, deptHistoryMapper, adjustmentSnapshotMapper,
                flowIntegration, null, null, null);
    }

    private Map<String, Object> adjustSnapshot(String action, boolean withDeptId) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("name", "研发三部");
        payload.put("code", "d_rd3");
        payload.put("parentId", 2L);
        payload.put("managerEmpId", 88L);
        payload.put("effectiveFrom", "2026-11-01");
        payload.put("effectiveTo", null);

        Map<String, Object> snap = new LinkedHashMap<>();
        snap.put("kind", "ORG_ADJUST_REQUEST");
        snap.put("bizKey", "ORG_ADJUST:123");
        snap.put("action", action);
        snap.put("deptId", withDeptId ? 456L : null);
        snap.put("payload", payload);
        snap.put("reason", "组织架构调整");
        snap.put("status", "PENDING");
        return snap;
    }

    private OrgAdjustmentSnapshot recordFor(Map<String, Object> snap) {
        OrgAdjustmentSnapshot record = new OrgAdjustmentSnapshot();
        record.setId(9L);
        record.setAdjustmentId(123L);
        record.setDeptId(456L);
        record.setSnapshotJson(com.hris.org.util.JsonUtil.toJson(snap));
        return record;
    }

    @Nested
    @DisplayName("幂等：非 PENDING 重复回调直接返回成功")
    class Idempotency {

        @Test
        @DisplayName("申请单已 APPROVED → changed=false，不重复应用变更")
        void skipsTerminalState() {
            Map<String, Object> snap = adjustSnapshot("UPDATE", true);
            snap.put("status", "APPROVED");
            when(adjustmentSnapshotMapper.selectOne(any())).thenReturn(recordFor(snap));

            ApprovalCallbackResponse resp = service.handleAdjustCallback("ORG_ADJUST:123", true, null);

            assertFalse(resp.changed());
            assertEquals("APPROVED", resp.status());
            verify(deptMapper, never()).insert(any(OrgDept.class));
            verify(adjustmentSnapshotMapper, never()).updateById(any(OrgAdjustmentSnapshot.class));
        }

        @Test
        @DisplayName("驳回后再回调同样幂等（REJECTED 终态）")
        void skipsRejectedState() {
            Map<String, Object> snap = adjustSnapshot("UPDATE", true);
            snap.put("status", "REJECTED");
            when(adjustmentSnapshotMapper.selectOne(any())).thenReturn(recordFor(snap));

            ApprovalCallbackResponse resp = service.handleAdjustCallback("ORG_ADJUST:123", true, null);

            assertFalse(resp.changed());
            assertEquals("REJECTED", resp.status());
        }
    }

    @Nested
    @DisplayName("状态更新与变更应用")
    class Apply {

        @Test
        @DisplayName("审批通过 + CREATE 动作 → 创建部门并置 APPROVED")
        void appliesCreateAction() {
            when(adjustmentSnapshotMapper.selectOne(any()))
                    .thenReturn(recordFor(adjustSnapshot("CREATE", false)));
            when(deptMapper.selectById(2L)).thenReturn(parent());
            when(deptMapper.selectCount(any())).thenReturn(0L);

            ApprovalCallbackResponse resp = service.handleAdjustCallback("ORG_ADJUST:123", true, null);

            assertTrue(resp.changed());
            assertEquals("APPROVED", resp.status());
            ArgumentCaptor<OrgDept> captor = ArgumentCaptor.forClass(OrgDept.class);
            verify(deptMapper).insert(captor.capture());
            assertEquals("研发三部", captor.getValue().getName());
            verify(adjustmentSnapshotMapper).updateById(any(OrgAdjustmentSnapshot.class));
        }

        private OrgDept parent() {
            OrgDept p = new OrgDept();
            p.setId(2L);
            p.setPath("1.2");
            return p;
        }

        @Test
        @DisplayName("审批通过 + UPDATE 动作 → 更新部门字段")
        void appliesUpdateAction() {
            when(adjustmentSnapshotMapper.selectOne(any()))
                    .thenReturn(recordFor(adjustSnapshot("UPDATE", true)));
            OrgDept dept = new OrgDept();
            dept.setId(456L);
            dept.setParentId(1L);
            dept.setName("研发二部");
            when(deptMapper.selectById(456L)).thenReturn(dept);
            when(deptMapper.selectById(2L)).thenReturn(parent());

            service.handleAdjustCallback("ORG_ADJUST:123", true, null);

            assertEquals("研发三部", dept.getName());
            verify(deptMapper).updateById(dept);
        }

        @Test
        @DisplayName("审批通过 + 未知动作（自由文本）→ 仅更新状态不应用变更")
        void updatesStatusOnlyForFreeTextAction() {
            when(adjustmentSnapshotMapper.selectOne(any()))
                    .thenReturn(recordFor(adjustSnapshot("合并大区", true)));

            ApprovalCallbackResponse resp = service.handleAdjustCallback("ORG_ADJUST:123", true, null);

            assertTrue(resp.changed());
            assertEquals("APPROVED", resp.status());
            verify(deptMapper, never()).insert(any(OrgDept.class));
            verify(deptMapper, never()).updateById(any(OrgDept.class));
        }

        @Test
        @DisplayName("驳回 → 置 REJECTED，不应用变更")
        void rejectsWithoutApply() {
            when(adjustmentSnapshotMapper.selectOne(any()))
                    .thenReturn(recordFor(adjustSnapshot("CREATE", false)));

            ApprovalCallbackResponse resp = service.handleAdjustCallback("ORG_ADJUST:123", false, "编制不足");

            assertTrue(resp.changed());
            assertEquals("REJECTED", resp.status());
            verify(deptMapper, never()).insert(any(OrgDept.class));
        }

        @Test
        @DisplayName("审批意见写入申请单 JSON approvalComment")
        void writesApprovalComment() {
            when(adjustmentSnapshotMapper.selectOne(any()))
                    .thenReturn(recordFor(adjustSnapshot("CREATE", false)));

            service.handleAdjustCallback("ORG_ADJUST:123", false, "编制不足");

            ArgumentCaptor<OrgAdjustmentSnapshot> captor =
                    ArgumentCaptor.forClass(OrgAdjustmentSnapshot.class);
            verify(adjustmentSnapshotMapper).updateById(captor.capture());
            assertTrue(captor.getValue().getSnapshotJson().contains("编制不足"));
        }
    }

    @Nested
    @DisplayName("守卫")
    class Guards {

        @Test
        @DisplayName("申请单不存在 → NOT_FOUND")
        void notFound() {
            when(adjustmentSnapshotMapper.selectOne(any())).thenReturn(null);

            BizException e = assertThrows(BizException.class,
                    () -> service.handleAdjustCallback("ORG_ADJUST:404", true, null));

            assertEquals(ErrorCode.NOT_FOUND.getCode(), e.getCode());
        }

        @Test
        @DisplayName("bizKey 非法（非 ORG_ADJUST: 前缀）→ NOT_FOUND")
        void malformedBizKey() {
            BizException e = assertThrows(BizException.class,
                    () -> service.handleAdjustCallback("OTHER:123", true, null));

            assertEquals(ErrorCode.NOT_FOUND.getCode(), e.getCode());
        }

        @Test
        @DisplayName("UPDATE 缺 deptId → PARAM_INVALID（保持 PENDING 由 flow 重试）")
        void updateWithoutDeptId() {
            when(adjustmentSnapshotMapper.selectOne(any()))
                    .thenReturn(recordFor(adjustSnapshot("UPDATE", false)));

            BizException e = assertThrows(BizException.class,
                    () -> service.handleAdjustCallback("ORG_ADJUST:123", true, null));

            assertEquals(ErrorCode.PARAM_INVALID.getCode(), e.getCode());
            verify(adjustmentSnapshotMapper, never()).updateById(any(OrgAdjustmentSnapshot.class));
        }
    }

    @Nested
    @DisplayName("写分离：调整申请落独立表，不污染 org_dept_history（B-7 根治）")
    class WriteSeparation {

        @Test
        @DisplayName("adjust() → 写 org_adjustment_snapshot，不写 org_dept_history")
        void adjustWritesSeparateTable() {
            DeptRequest payload = new DeptRequest("研发三部", "d_rd3", 2L, 88L,
                    java.time.LocalDate.parse("2026-11-01"), null, null);
            AdjustRequestCreate req = new AdjustRequestCreate("CREATE", 456L, payload, "组织架构调整");
            when(flowIntegration.startOrgAdjust(any())).thenReturn("flow-1");

            service.adjust(req);

            verify(adjustmentSnapshotMapper).insert(any(OrgAdjustmentSnapshot.class));
            verify(deptHistoryMapper, never()).insert(any(com.hris.org.entity.OrgDeptHistory.class));
        }

        @Test
        @DisplayName("申请单 JSON 含 kind=ORG_ADJUST_REQUEST 与 bizKey=ORG_ADJUST:{id}")
        void adjustSnapshotCarriesKindAndBizKey() {
            DeptRequest payload = new DeptRequest("研发三部", "d_rd3", 2L, 88L,
                    java.time.LocalDate.parse("2026-11-01"), null, null);
            AdjustRequestCreate req = new AdjustRequestCreate("CREATE", 456L, payload, "组织架构调整");
            when(flowIntegration.startOrgAdjust(any())).thenReturn("flow-1");

            service.adjust(req);

            ArgumentCaptor<OrgAdjustmentSnapshot> captor =
                    ArgumentCaptor.forClass(OrgAdjustmentSnapshot.class);
            verify(adjustmentSnapshotMapper).insert(captor.capture());
            String json = captor.getValue().getSnapshotJson();
            assertTrue(json.contains("ORG_ADJUST_REQUEST"));
            assertTrue(json.contains("ORG_ADJUST:"));
            assertEquals(456L, captor.getValue().getDeptId().longValue());
        }
    }
}
