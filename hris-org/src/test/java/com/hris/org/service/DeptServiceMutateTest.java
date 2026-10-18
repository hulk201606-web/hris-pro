package com.hris.org.service;

import com.hris.common.exception.BizException;
import com.hris.common.exception.ErrorCode;
import com.hris.org.dto.AdjustRequestCreate;
import com.hris.org.dto.AdjustRequestResponse;
import com.hris.org.dto.DeptRequest;
import com.hris.org.entity.OrgDept;
import com.hris.org.entity.OrgDeptHistory;
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

import java.time.LocalDate;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * DeptService 变更/快照单元测试（ORG-F-004/005）。
 *
 * <p>与 DeptServiceCrudTest 分文件的原因：本类聚焦"先快照后变更"的时序与历史回落，
 * 而CrudTest 聚焦建档时的深度/编码/父部门校验；两类守卫的 arrange 方式不同，混在一起会超 300 行门禁。</p>
 */
@ExtendWith(MockitoExtension.class)
class DeptServiceMutateTest {

    @Mock
    private OrgDeptMapper deptMapper;
    @Mock
    private OrgDeptHistoryMapper deptHistoryMapper;
    @Mock
    private FlowIntegration flowIntegration;

    private DeptService service;

    @BeforeEach
    void setUp() {
        service = new DeptService(deptMapper, deptHistoryMapper, flowIntegration);
    }

    private OrgDept parent(long id, int depth) {
        // path 形如 1.2.3...，段数 = depth
        StringBuilder sb = new StringBuilder();
        for (int i = 1; i <= depth; i++) {
            if (i > 1) {
                sb.append('.');
            }
            sb.append(i);
        }
        OrgDept d = new OrgDept();
        d.setId(id);
        d.setPath(sb.toString());
        d.setName("父部门" + id);
        d.setStatus(1);
        return d;
    }

    private DeptRequest req(Long parentId, String code, String name) {
        return new DeptRequest(name, code, parentId, 900L, LocalDate.now(), null, null);
    }

    @Nested
    @DisplayName("更新：先落历史快照再改，且变更父部门需重新校验深度")
    class Update {

        private OrgDept existing() {
            OrgDept d = new OrgDept();
            d.setId(20L);
            d.setParentId(10L);
            d.setPath("1.2.20");
            d.setName("原部门");
            d.setCode("d20");
            d.setStatus(1);
            return d;
        }

        @Test
        @DisplayName("部门不存在 → NOT_FOUND")
        void deptNotFound() {
            when(deptMapper.selectById(404L)).thenReturn(null);

            BizException e = assertThrows(BizException.class,
                    () -> service.update(404L, req(null, null, "改名")));

            assertEquals(ErrorCode.NOT_FOUND.getCode(), e.getCode());
        }

        @Test
        @DisplayName("更新前先写入历史快照（快照内容为改动前的原值）")
        void writesSnapshotBeforeUpdate() {
            when(deptMapper.selectById(20L)).thenReturn(existing());

            service.update(20L, new DeptRequest("新名称", null, null, null,
                    LocalDate.now(), null, null));

            ArgumentCaptor<OrgDeptHistory> captor = ArgumentCaptor.forClass(OrgDeptHistory.class);
            verify(deptHistoryMapper).insert(captor.capture());
            assertEquals(20L, captor.getValue().getDeptId());
            assertTrue(captor.getValue().getSnapshot().contains("原部门"),
                    "快照应是改动前的名称，实际=" + captor.getValue().getSnapshot());
        }

        @Test
        @DisplayName("name 为空白 → 不覆盖原名称（局部更新语义，非置空）")
        void blankNameDoesNotOverwrite() {
            when(deptMapper.selectById(20L)).thenReturn(existing());

            service.update(20L, new DeptRequest("   ", null, null, null,
                    LocalDate.now(), null, null));

            ArgumentCaptor<OrgDept> captor = ArgumentCaptor.forClass(OrgDept.class);
            verify(deptMapper).updateById(captor.capture());
            assertEquals("原部门", captor.getValue().getName());
        }

        @Test
        @DisplayName("变更父部门到超限深度 → PARAM_INVALID（更新不得绕过建档时的深度校验）")
        void revalidatesDepthOnParentChange() {
            when(deptMapper.selectById(20L)).thenReturn(existing());
            when(deptMapper.selectById(30L)).thenReturn(parent(30L, 8));

            assertThrows(BizException.class,
                    () -> service.update(20L, req(30L, null, "换父")));

            verify(deptMapper, never()).updateById(any(OrgDept.class));
        }
    }

    @Nested
    @DisplayName("历史快照三级回落")
    class Snapshot {

        @Test
        @DisplayName("命中历史 → 返回该快照并回填 deptId/snapshotDate")
        void returnsHistoryWhenFound() {
            OrgDeptHistory h = new OrgDeptHistory();
            h.setDeptId(20L);
            h.setSnapshotDate(LocalDate.now().minusDays(1));
            h.setSnapshot("{\"name\":\"历史部门\"}");
            when(deptHistoryMapper.selectOne(any())).thenReturn(h);

            Map<String, Object> snap = service.snapshot(20L, LocalDate.now());

            assertEquals("历史部门", snap.get("name"));
            assertEquals(20L, snap.get("deptId"));
            assertNotNull(snap.get("snapshotDate"));
        }

        @Test
        @DisplayName("无历史 → 回落当前活部门")
        void fallsBackToLiveDept() {
            when(deptHistoryMapper.selectOne(any())).thenReturn(null);
            OrgDept d = new OrgDept();
            d.setId(20L);
            d.setName("当前部门");
            d.setCode("d20");
            d.setStatus(1);
            when(deptMapper.selectById(20L)).thenReturn(d);

            assertEquals("当前部门", service.snapshot(20L, null).get("name"));
        }

        @Test
        @DisplayName("无历史且部门不存在 → NOT_FOUND")
        void notFoundWhenDeptMissing() {
            when(deptHistoryMapper.selectOne(any())).thenReturn(null);
            when(deptMapper.selectById(404L)).thenReturn(null);

            BizException e = assertThrows(BizException.class, () -> service.snapshot(404L, null));

            assertEquals(ErrorCode.NOT_FOUND.getCode(), e.getCode());
        }
    }

    @Nested
    @DisplayName("组织调整申请：flow 未就绪时降级仅落单")
    class Adjust {

        @Test
        @DisplayName("flow 降级返回 null → 仍返回 PENDING，不阻断建单（docs/06 §4）")
        void degradesWhenFlowUnavailable() {
            when(flowIntegration.startOrgAdjust(any())).thenReturn(null);

            AdjustRequestResponse resp = service.adjust(new AdjustRequestCreate(
                    "CREATE", 20L, req(0L, "adj", "调整申请"), "扩编"));

            assertEquals("PENDING", resp.status());
            assertNotNull(resp.bizKey());
            verify(deptHistoryMapper).insert(any(OrgDeptHistory.class));
        }
    }
}
