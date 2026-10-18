package com.hris.org.service;

import com.hris.common.api.R;
import com.hris.common.exception.BizException;
import com.hris.common.exception.ErrorCode;
import com.hris.org.client.EmployeeClient;
import com.hris.org.entity.OrgDept;
import com.hris.org.mapper.OrgDeptMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

/**
 * ReportLineService 单元测试（ORG-F-019：直属上级 → 部门负责人 → 上级部门负责人）。
 *
 * <p>两道关键守卫：①防环——部门父链可能成环（脏数据或迁移遗留），visited 集合防止死循环；
 * ②去重——同一人可能既是直属上级又是部门负责人，不应重复出现在链上。</p>
 */
@ExtendWith(MockitoExtension.class)
class ReportLineServiceTest {

    @Mock
    private EmployeeClient employeeClient;
    @Mock
    private OrgDeptMapper deptMapper;

    private ReportLineService service;

    @BeforeEach
    void setUp() {
        service = new ReportLineService(employeeClient, deptMapper);
    }

    private Map<String, Object> profile(Long deptId, Long supervisorEmpId) {
        Map<String, Object> m = new HashMap<>();
        m.put("empId", 100L);
        m.put("deptId", deptId);
        m.put("supervisorEmpId", supervisorEmpId);
        return m;
    }

    private OrgDept dept(Long id, Long parentId, Long managerEmpId) {
        OrgDept d = new OrgDept();
        d.setId(id);
        d.setParentId(parentId);
        d.setManagerEmpId(managerEmpId);
        d.setStatus(1);
        return d;
    }

    @Nested
    @DisplayName("降级与异常")
    class Degraded {

        @Test
        @DisplayName("员工域服务不可用（抛异常）→ EMP_ERROR「汇报线解析暂不可用」")
        void employeeServiceDownThrowsEmpError() {
            when(employeeClient.profileBrief(anyLong())).thenThrow(new RuntimeException("connect refused"));

            BizException e = assertThrows(BizException.class, () -> service.resolve(100L));

            assertEquals(ErrorCode.EMP_ERROR.getCode(), e.getCode());
        }

        @Test
        @DisplayName("档案数据为 null（员工不存在）→ NOT_FOUND")
        void nullProfileThrowsNotFound() {
            when(employeeClient.profileBrief(anyLong())).thenReturn(R.ok(null));

            BizException e = assertThrows(BizException.class, () -> service.resolve(100L));

            assertEquals(ErrorCode.NOT_FOUND.getCode(), e.getCode());
        }
    }

    @Nested
    @DisplayName("三级降级链")
    class Chain {

        @Test
        @DisplayName("有直属上级 → 链首为直属上级")
        void startsWithDirectSupervisor() {
            when(employeeClient.profileBrief(100L)).thenReturn(R.ok(profile(1L, 200L)));
            when(deptMapper.selectById(1L)).thenReturn(dept(1L, 0L, 300L));

            List<Long> chain = service.resolve(100L);

            assertEquals(200L, chain.get(0), "直属上级应排在最前");
            assertTrue(chain.contains(300L), "随后应降级到本部门负责人");
        }

        @Test
        @DisplayName("无直属上级 → 链首为本部门负责人")
        void fallsBackToDeptManager() {
            when(employeeClient.profileBrief(100L)).thenReturn(R.ok(profile(1L, null)));
            when(deptMapper.selectById(1L)).thenReturn(dept(1L, 0L, 300L));

            assertEquals(List.of(300L), service.resolve(100L));
        }

        @Test
        @DisplayName("逐级向上：本人部门无负责人时取上级部门负责人")
        void walksUpToParentDeptManager() {
            when(employeeClient.profileBrief(100L)).thenReturn(R.ok(profile(1L, null)));
            when(deptMapper.selectById(1L)).thenReturn(dept(1L, 2L, null));
            when(deptMapper.selectById(2L)).thenReturn(dept(2L, 0L, 400L));

            assertEquals(List.of(400L), service.resolve(100L), "应向上找到上级部门负责人");
        }

        @Test
        @DisplayName("中途部门不存在 → 停止向上遍历，返回已收集部分")
        void stopsWhenDeptMissing() {
            when(employeeClient.profileBrief(100L)).thenReturn(R.ok(profile(1L, null)));
            when(deptMapper.selectById(1L)).thenReturn(dept(1L, 99L, 300L));
            when(deptMapper.selectById(99L)).thenReturn(null);

            assertEquals(List.of(300L), service.resolve(100L));
        }
    }

    @Nested
    @DisplayName("防环与去重")
    class Guard {

        @Test
        @DisplayName("部门父链成环 → 防环守卫生效不死循环，返回有限链")
        void cyclicDeptChainTerminates() {
            when(employeeClient.profileBrief(100L)).thenReturn(R.ok(profile(1L, null)));
            // 1 的父是 2、2 的父是 1 —— 脏数据场景
            when(deptMapper.selectById(1L)).thenReturn(dept(1L, 2L, 300L));
            when(deptMapper.selectById(2L)).thenReturn(dept(2L, 1L, 400L));

            List<Long> chain = service.resolve(100L);

            assertTrue(chain.size() <= 2, "成环时链长应受限，实际=" + chain);
        }

        @Test
        @DisplayName("同一人既是直属上级又是部门负责人 → 只出现一次")
        void dedupsSameManager() {
            // 直属上级 200，且 1 部门负责人也是 200
            when(employeeClient.profileBrief(100L)).thenReturn(R.ok(profile(1L, 200L)));
            when(deptMapper.selectById(1L)).thenReturn(dept(1L, 0L, 200L));

            List<Long> chain = service.resolve(100L);

            assertEquals(1, chain.stream().filter(v -> v.equals(200L)).count(),
                    "同一人不应在汇报线上重复出现，实际=" + chain);
        }
    }
}