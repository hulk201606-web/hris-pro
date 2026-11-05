package com.hris.org.service;

import com.hris.common.api.R;
import com.hris.common.exception.BizException;
import com.hris.common.exception.ErrorCode;
import com.hris.org.client.EmployeeClient;
import com.hris.org.dto.DeptMoveRequest;
import com.hris.org.entity.OrgDept;
import com.hris.org.entity.OrgDeptHistory;
import com.hris.org.entity.OrgHeadcount;
import com.hris.org.entity.OrgHcOccupancy;
import com.hris.org.mapper.OrgAdjustmentSnapshotMapper;
import com.hris.org.mapper.OrgDeptHistoryMapper;
import com.hris.org.mapper.OrgDeptMapper;
import com.hris.org.mapper.OrgHeadcountMapper;
import com.hris.org.mapper.OrgHcOccupancyMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * DeptService 删除守卫 + 快照过滤 + move 单元测试（ORG-F-018 深化 / §10.5-B7 / ORG-F-002 深化）。
 *
 * <p>删除是组织数据的高危操作，四类守卫（子部门/在职员工/占用流水/服务异常）任何一环
 * 失守都会造成组织树断链或编制悬空；B-7 根治后申请单 JSON 落 org_adjustment_snapshot
 * 独立表，org_dept_history 回归纯历史语义，快照查询不再需要过滤申请单行。</p>
 */
@ExtendWith(MockitoExtension.class)
class DeptServiceDeleteTest {

    @Mock
    private OrgDeptMapper deptMapper;
    @Mock
    private OrgDeptHistoryMapper deptHistoryMapper;
    @Mock
    private OrgAdjustmentSnapshotMapper adjustmentSnapshotMapper;
    @Mock
    private FlowIntegration flowIntegration;
    @Mock
    private EmployeeClient employeeClient;
    @Mock
    private OrgHeadcountMapper headcountMapper;
    @Mock
    private OrgHcOccupancyMapper occupancyMapper;

    private DeptService service;

    @BeforeEach
    void setUp() {
        service = new DeptService(deptMapper, deptHistoryMapper, adjustmentSnapshotMapper,
                flowIntegration, employeeClient, headcountMapper, occupancyMapper);
    }

    private OrgDept dept(Long id, Long parentId, String path) {
        OrgDept d = new OrgDept();
        d.setId(id);
        d.setParentId(parentId);
        d.setPath(path);
        d.setName("研发部");
        d.setCode("d001");
        d.setStatus(1);
        return d;
    }

    @Nested
    @DisplayName("delete：四类守卫")
    class Delete {

        @Test
        @DisplayName("部门不存在 → NOT_FOUND")
        void rejectsMissingDept() {
            when(deptMapper.selectById(404L)).thenReturn(null);

            BizException e = assertThrows(BizException.class, () -> service.delete(404L));

            assertEquals(ErrorCode.NOT_FOUND.getCode(), e.getCode());
        }

        @Test
        @DisplayName("守卫一：有子部门 → ORG_ERROR，不触达员工服务")
        void rejectsWhenHasChildren() {
            when(deptMapper.selectById(1L)).thenReturn(dept(1L, 0L, "1"));
            when(deptMapper.selectCount(any())).thenReturn(2L);

            BizException e = assertThrows(BizException.class, () -> service.delete(1L));

            assertEquals(ErrorCode.ORG_ERROR.getCode(), e.getCode());
            verify(employeeClient, never()).countActiveByDept(any());
        }

        @Test
        @DisplayName("守卫二：有在职员工 → ORG_ERROR")
        void rejectsWhenHasActiveEmployees() {
            when(deptMapper.selectById(1L)).thenReturn(dept(1L, 0L, "1"));
            when(deptMapper.selectCount(any())).thenReturn(0L);
            when(employeeClient.countActiveByDept(1L)).thenReturn(R.ok(3L));

            BizException e = assertThrows(BizException.class, () -> service.delete(1L));

            assertEquals(ErrorCode.ORG_ERROR.getCode(), e.getCode());
            verify(deptMapper, never()).deleteById(1L);
        }

        @Test
        @DisplayName("守卫三：有编制占用流水 → ORG_ERROR")
        void rejectsWhenHasOccupancyRows() {
            when(deptMapper.selectById(1L)).thenReturn(dept(1L, 0L, "1"));
            when(deptMapper.selectCount(any())).thenReturn(0L);
            when(employeeClient.countActiveByDept(1L)).thenReturn(R.ok(0L));
            when(occupancyMapper.selectCount(any())).thenReturn(5L);

            BizException e = assertThrows(BizException.class, () -> service.delete(1L));

            assertEquals(ErrorCode.ORG_ERROR.getCode(), e.getCode());
            verify(deptMapper, never()).deleteById(1L);
        }

        @Test
        @DisplayName("守卫四：员工服务不可用/异常 → fail-closed 拒绝删除")
        void rejectsWhenEmployeeServiceDown() {
            when(deptMapper.selectById(1L)).thenReturn(dept(1L, 0L, "1"));
            when(deptMapper.selectCount(any())).thenReturn(0L);
            when(employeeClient.countActiveByDept(1L)).thenThrow(new RuntimeException("connect timeout"));

            BizException e = assertThrows(BizException.class, () -> service.delete(1L));

            assertEquals(ErrorCode.ORG_ERROR.getCode(), e.getCode());
            verify(deptMapper, never()).deleteById(1L);
        }

        @Test
        @DisplayName("守卫全过 → 逻辑删部门 + 一并删编制计划行 + 落删除前快照")
        void deletesDeptAndHeadcountWithSnapshot() {
            OrgDept d = dept(1L, 0L, "1");
            when(deptMapper.selectById(1L)).thenReturn(d);
            when(deptMapper.selectCount(any())).thenReturn(0L);
            when(employeeClient.countActiveByDept(1L)).thenReturn(R.ok(0L));
            when(occupancyMapper.selectCount(any())).thenReturn(0L);

            service.delete(1L);

            verify(deptMapper).deleteById(1L);
            verify(headcountMapper).delete(any());
            verify(deptHistoryMapper).insert(any(OrgDeptHistory.class));
        }
    }

    @Nested
    @DisplayName("move：循环校验 + 兄弟序列重排")
    class Move {

        @Test
        @DisplayName("移到自身子孙 → PARAM_INVALID（自祖循环守卫）")
        void rejectsMoveToOwnDescendant() {
            // 1 -> 1.12 -> 1.12.130，把 1 移到 130（1 的子孙）下构成循环
            when(deptMapper.selectById(1L)).thenReturn(dept(1L, 0L, "1"));
            when(deptMapper.selectById(130L)).thenReturn(dept(130L, 12L, "1.12.130"));

            BizException e = assertThrows(BizException.class,
                    () -> service.move(1L, new DeptMoveRequest(130L, 0)));

            assertEquals(ErrorCode.PARAM_INVALID.getCode(), e.getCode());
            verify(deptMapper, never()).updateById(any(OrgDept.class));
        }

        @Test
        @DisplayName("合法移动 → 更新 parent/path/sortOrder 并重排兄弟序列")
        void movesAndReordersSiblings() {
            when(deptMapper.selectById(30L)).thenReturn(dept(30L, 1L, "1.30"));
            when(deptMapper.selectById(2L)).thenReturn(dept(2L, 0L, "2"));
            // 新兄弟：40(sortOrder=0)、50(sortOrder=1)、以及被移动的 30(sortOrder=5)
            OrgDept s40 = dept(40L, 2L, "2.40");
            s40.setSortOrder(0);
            OrgDept s50 = dept(50L, 2L, "2.50");
            s50.setSortOrder(1);
            when(deptMapper.selectList(any())).thenReturn(List.of(s40, s50));

            service.move(30L, new DeptMoveRequest(2L, 2));

            verify(deptMapper).updateById(any(OrgDept.class));
            // 兄弟序列重排：40→0、50→1（原本已是 0/1，无需再写）
            assertEquals(0, s40.getSortOrder());
            assertEquals(1, s50.getSortOrder());
        }

        @Test
        @DisplayName("目标父不存在 → NOT_FOUND")
        void rejectsMissingParent() {
            when(deptMapper.selectById(30L)).thenReturn(dept(30L, 1L, "1.30"));
            when(deptMapper.selectById(999L)).thenReturn(null);

            BizException e = assertThrows(BizException.class,
                    () -> service.move(30L, new DeptMoveRequest(999L, 0)));

            assertEquals(ErrorCode.NOT_FOUND.getCode(), e.getCode());
        }

        @Test
        @DisplayName("重排时同序号冲突按 id 兜底，写入连续序号")
        void reordersWithSequentialIndexes() {
            OrgDept moved = dept(30L, 1L, "1.30");
            moved.setSortOrder(5); // 更新前的旧序号（模拟库中现状）
            when(deptMapper.selectById(30L)).thenReturn(moved);
            when(deptMapper.selectById(2L)).thenReturn(dept(2L, 0L, "2"));
            OrgDept s40 = dept(40L, 2L, "2.40");
            s40.setSortOrder(null); // 脏数据兜底：null 排最后
            // 兄弟查询包含被移动节点自身（move 已更新其 sortOrder=0）
            when(deptMapper.selectList(any())).thenReturn(new java.util.ArrayList<>(List.of(moved, s40)));

            service.move(30L, new DeptMoveRequest(2L, 0));

            ArgumentCaptor<OrgDept> captor = ArgumentCaptor.forClass(OrgDept.class);
            verify(deptMapper, org.mockito.Mockito.atLeastOnce()).updateById(captor.capture());
            // 被移动节点拿到 0，原 null 的 40 排在其后拿 1
            assertEquals(0, moved.getSortOrder());
            assertEquals(1, s40.getSortOrder());
        }
    }

    @Nested
    @DisplayName("snapshot：历史快照查询（B-7 根治后申请单落独立表，查询无需过滤）")
    class SnapshotFilter {

        @Test
        @DisplayName("历史表无真实快照（申请单行已在独立表，不再混入）→ 用现行部门现拼快照兜底")
        void fallsBackToCurrentDeptWhenOnlyRequestRows() {
            when(deptHistoryMapper.selectOne(any())).thenReturn(null);
            when(deptMapper.selectById(1L)).thenReturn(dept(1L, 0L, "1"));

            var snap = service.snapshot(1L, null);

            assertEquals(1L, ((Number) snap.get("deptId")).longValue());
            assertEquals("研发部", snap.get("name"));
        }

        @Test
        @DisplayName("存在真实快照行 → 直接返回该快照（申请单 JSON 已由独立表隔离）")
        void returnsRealSnapshotRow() {
            OrgDeptHistory row = new OrgDeptHistory();
            row.setDeptId(1L);
            row.setSnapshotDate(java.time.LocalDate.now().minusDays(1));
            row.setSnapshot("{\"id\":1,\"name\":\"研发部\",\"code\":\"d001\"}");
            when(deptHistoryMapper.selectOne(any())).thenReturn(row);

            var snap = service.snapshot(1L, null);

            assertEquals("研发部", snap.get("name"));
            assertEquals(1L, ((Number) snap.get("deptId")).longValue());
            verify(deptMapper, never()).selectById(1L);
        }

        @Test
        @DisplayName("无历史且部门不存在 → NOT_FOUND")
        void rejectsMissingDept() {
            when(deptHistoryMapper.selectOne(any())).thenReturn(null);
            when(deptMapper.selectById(404L)).thenReturn(null);

            BizException e = assertThrows(BizException.class, () -> service.snapshot(404L, null));

            assertEquals(ErrorCode.NOT_FOUND.getCode(), e.getCode());
        }
    }
}
