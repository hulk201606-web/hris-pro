package com.hris.employee.service;

import com.hris.employee.dto.ReportDtos.HeadcountStructureBriefRow;
import com.hris.employee.entity.Employee;
import com.hris.employee.entity.Employment;
import com.hris.employee.mapper.EmployeeMapper;
import com.hris.employee.mapper.EmploymentMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * 人力结构简报服务单测（P1 报表数据源）：现行口径/asOf 口径行映射、无雇佣关系兜底、空数据。
 */
@ExtendWith(MockitoExtension.class)
class HeadcountStructureBriefServiceTest {

    @Mock
    private EmploymentMapper employmentMapper;
    @Mock
    private EmployeeMapper employeeMapper;

    private HeadcountStructureBriefService service;

    @BeforeEach
    void setUp() {
        service = new HeadcountStructureBriefService(employmentMapper, employeeMapper);
    }

    private Employee emp(long id, Integer status) {
        Employee e = new Employee();
        e.setId(id);
        e.setStatus(status);
        return e;
    }

    private Employment employment(long empId, Long legalEntityId, Long deptId, String hireType) {
        Employment e = new Employment();
        e.setEmpId(empId);
        e.setLegalEntityId(legalEntityId);
        e.setDeptId(deptId);
        e.setHireType(hireType);
        e.setStatus(1);
        return e;
    }

    @Nested
    @DisplayName("现行口径（asOf=null）")
    class CurrentScope {

        @Test
        @DisplayName("员工×现行雇佣关系行映射：法人/部门/雇佣类型 + 主档状态码")
        void mapsEmploymentFields() {
            when(employeeMapper.selectList(any())).thenReturn(List.of(
                    emp(100L, 2), emp(200L, 4)));
            when(employmentMapper.selectList(any())).thenReturn(List.of(
                    employment(100L, 10L, 5L, "FULLTIME")));

            List<HeadcountStructureBriefRow> rows = service.rows(null);

            assertEquals(2, rows.size());
            HeadcountStructureBriefRow r100 = rows.stream()
                    .filter(r -> r.empId() == 100L).findFirst().orElseThrow();
            assertEquals(10L, r100.legalEntityId());
            assertEquals(5L, r100.deptId());
            assertEquals("FULLTIME", r100.hireType());
            assertEquals(2, r100.status());
            HeadcountStructureBriefRow r200 = rows.stream()
                    .filter(r -> r.empId() == 200L).findFirst().orElseThrow();
            assertNull(r200.legalEntityId());
            assertNull(r200.deptId());
            assertNull(r200.hireType());
            assertEquals(4, r200.status());
        }

        @Test
        @DisplayName("离职员工（无现行雇佣）仍返回行，供按状态维度覆盖")
        void includesResignedEmployees() {
            when(employeeMapper.selectList(any())).thenReturn(List.of(emp(200L, 4)));
            when(employmentMapper.selectList(any())).thenReturn(List.of());

            List<HeadcountStructureBriefRow> rows = service.rows(null);

            assertEquals(1, rows.size());
            assertEquals(4, rows.get(0).status());
        }

        @Test
        @DisplayName("有雇佣关系但主档缺失的兜底行：status=null，聚合不丢人头")
        void includesOrphanEmployment() {
            when(employeeMapper.selectList(any())).thenReturn(List.of(emp(100L, 2)));
            when(employmentMapper.selectList(any())).thenReturn(List.of(
                    employment(100L, 10L, 5L, "FULLTIME"),
                    employment(999L, 11L, 6L, "INTERN")));

            List<HeadcountStructureBriefRow> rows = service.rows(null);

            assertEquals(2, rows.size());
            HeadcountStructureBriefRow orphan = rows.stream()
                    .filter(r -> r.empId() == 999L).findFirst().orElseThrow();
            assertNull(orphan.status());
            assertEquals(11L, orphan.legalEntityId());
        }

        @Test
        @DisplayName("无员工主档 → 空列表")
        void emptyWhenNoEmployees() {
            when(employeeMapper.selectList(any())).thenReturn(List.of());

            assertTrue(service.rows(null).isEmpty());
        }
    }

    @Nested
    @DisplayName("asOf 口径")
    class AsOfScope {

        @Test
        @DisplayName("指定 asOf 时行映射同样成立（时点版本过滤由查询条件承担）")
        void mapsRowsWithAsOf() {
            when(employeeMapper.selectList(any())).thenReturn(List.of(emp(100L, 1)));
            when(employmentMapper.selectList(any())).thenReturn(List.of(
                    employment(100L, 12L, 7L, "INTERN")));

            List<HeadcountStructureBriefRow> rows = service.rows(LocalDate.of(2026, 1, 31));

            assertEquals(1, rows.size());
            assertEquals(12L, rows.get(0).legalEntityId());
            assertEquals(1, rows.get(0).status());
        }
    }
}
