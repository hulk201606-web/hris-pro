package com.hris.org.service;

import com.hris.common.api.R;
import com.hris.common.exception.BizException;
import com.hris.org.client.EmployeeClient;
import com.hris.org.dto.ReportDtos.DeptCount;
import com.hris.org.dto.ReportDtos.HeadcountStructureBriefRequest;
import com.hris.org.dto.ReportDtos.HeadcountStructureBriefRow;
import com.hris.org.dto.ReportDtos.HeadcountStructureResponse;
import com.hris.org.dto.ReportDtos.HireTypeCount;
import com.hris.org.dto.ReportDtos.StatusCount;
import com.hris.org.entity.OrgDept;
import com.hris.org.mapper.OrgDeptMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * P1 人力结构分析报表单测：部门树聚合（一级滚动/逐部门）、雇佣类型/状态维度、emp 降级。
 */
@ExtendWith(MockitoExtension.class)
class HeadcountStructureReportServiceTest {

    @Mock
    private EmployeeClient employeeClient;
    @Mock
    private OrgDeptMapper deptMapper;

    private HeadcountStructureReportService service;

    @BeforeEach
    void setUp() {
        service = new HeadcountStructureReportService(employeeClient, deptMapper);
    }

    private OrgDept dept(long id, String path, String name) {
        OrgDept d = new OrgDept();
        d.setId(id);
        d.setPath(path);
        d.setName(name);
        return d;
    }

    private HeadcountStructureBriefRow row(long empId, Long deptId, String hireType, Integer status) {
        return new HeadcountStructureBriefRow(empId, 10L, deptId, hireType, status);
    }

    private void givenBriefRows(List<HeadcountStructureBriefRow> rows) {
        when(employeeClient.headcountStructureBrief(any(HeadcountStructureBriefRequest.class)))
                .thenReturn(R.ok(rows));
    }

    @Nested
    @DisplayName("部门维度")
    class DeptDimension {

        @Test
        @DisplayName("top 口径：下级部门人数滚动聚合到一级部门（按 path 首段）")
        void rollsUpToTopLevel() {
            givenBriefRows(List.of(
                    row(1L, 2L, "FULLTIME", 2),
                    row(2L, 2L, "FULLTIME", 2),
                    row(3L, 3L, "INTERN", 1),
                    row(4L, 1L, "FULLTIME", 2)));
            when(deptMapper.selectList(any())).thenReturn(List.of(
                    dept(1L, "1", "总部"),
                    dept(2L, "1.2", "研发部"),
                    dept(3L, "1.2.3", "后端组")));

            HeadcountStructureResponse resp = service.structure("top");

            assertEquals(1, resp.byDept().size());
            DeptCount d = resp.byDept().get(0);
            assertEquals(1L, d.deptId());
            assertEquals("总部", d.deptName());
            assertEquals(4, d.count());
        }

        @Test
        @DisplayName("all 口径：按员工直属部门逐部门计数并富化名称")
        void countsPerDeptForAllScope() {
            givenBriefRows(List.of(
                    row(1L, 2L, "FULLTIME", 2),
                    row(2L, 2L, "FULLTIME", 2),
                    row(3L, 3L, "INTERN", 1)));
            when(deptMapper.selectList(any())).thenReturn(List.of(
                    dept(1L, "1", "总部"),
                    dept(2L, "1.2", "研发部"),
                    dept(3L, "1.2.3", "后端组")));

            HeadcountStructureResponse resp = service.structure("all");

            assertEquals(2, resp.byDept().size());
            DeptCount d2 = resp.byDept().stream().filter(x -> x.deptId() == 2L).findFirst().orElseThrow();
            assertEquals("研发部", d2.deptName());
            assertEquals(2, d2.count());
            DeptCount d3 = resp.byDept().stream().filter(x -> x.deptId() == 3L).findFirst().orElseThrow();
            assertEquals(1, d3.count());
        }

        @Test
        @DisplayName("未知部门/无雇佣关系员工：top 口径回退自身 id，名称降级 null")
        void fallsBackWhenDeptUnknown() {
            givenBriefRows(List.of(
                    row(1L, 99L, "FULLTIME", 2),
                    row(2L, null, "FULLTIME", 2)));
            when(deptMapper.selectList(any())).thenReturn(List.of(dept(1L, "1", "总部")));

            HeadcountStructureResponse resp = service.structure(null);

            assertEquals(1, resp.byDept().size());
            assertNull(resp.byDept().get(0).deptName());
            assertEquals(1, resp.byDept().get(0).count());
        }
    }

    @Nested
    @DisplayName("类型与状态维度")
    class TypeAndStatusDimension {

        @Test
        @DisplayName("雇佣类型与在职状态分组计数正确")
        void groupsHireTypeAndStatus() {
            givenBriefRows(List.of(
                    row(1L, 1L, "FULLTIME", 2),
                    row(2L, 1L, "FULLTIME", 1),
                    row(3L, 1L, "INTERN", 2),
                    row(4L, 1L, "FULLTIME", 4),
                    row(5L, 1L, "PARTTIME", 3)));

            HeadcountStructureResponse resp = service.structure("top");

            assertEquals(3, resp.byHireType().size());
            assertEquals(3, resp.byHireType().stream()
                    .filter(t -> "FULLTIME".equals(t.hireType())).findFirst().orElseThrow().count());
            assertEquals(1, resp.byHireType().stream()
                    .filter(t -> "INTERN".equals(t.hireType())).findFirst().orElseThrow().count());
            assertEquals(4, resp.byStatus().size());
            assertEquals(2, resp.byStatus().stream()
                    .filter(s -> s.status() == 2).findFirst().orElseThrow().count());
        }

        @Test
        @DisplayName("hireType/status 缺失的行不进入对应维度计数")
        void skipsNullDimensions() {
            givenBriefRows(List.of(
                    new HeadcountStructureBriefRow(1L, 10L, 1L, null, null)));

            HeadcountStructureResponse resp = service.structure("top");

            assertTrue(resp.byHireType().isEmpty());
            assertTrue(resp.byStatus().isEmpty());
        }
    }

    @Nested
    @DisplayName("参数与降级")
    class ParamAndDegradation {

        @Test
        @DisplayName("非法 deptScope → PARAM_INVALID")
        void rejectsInvalidScope() {
            assertThrows(BizException.class, () -> service.structure("bogus"));
        }

        @Test
        @DisplayName("emp 不可用 → 三维度降级为空列表（fail-open）")
        void degradesWhenEmpUnavailable() {
            when(employeeClient.headcountStructureBrief(any(HeadcountStructureBriefRequest.class)))
                    .thenThrow(new RuntimeException("emp down"));

            HeadcountStructureResponse resp = service.structure("top");

            assertTrue(resp.byDept().isEmpty());
            assertTrue(resp.byHireType().isEmpty());
            assertTrue(resp.byStatus().isEmpty());
        }

        @Test
        @DisplayName("emp 返回空数据 → 三维度空列表")
        void emptyWhenNoEmployees() {
            givenBriefRows(List.of());

            HeadcountStructureResponse resp = service.structure("top");

            assertTrue(resp.byDept().isEmpty());
            assertTrue(resp.byHireType().isEmpty());
            assertTrue(resp.byStatus().isEmpty());
        }
    }
}
