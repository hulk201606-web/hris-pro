package com.hris.employee.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hris.employee.dto.EmployeeBrief;
import com.hris.employee.dto.PageResult;
import com.hris.employee.entity.Employee;
import com.hris.employee.entity.Employment;
import com.hris.employee.mapper.EmployeeMapper;
import com.hris.employee.mapper.EmploymentMapper;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 花名册多维筛选 + 分页查询（EMP-F-022）。
 *
 * <p>从 EmployeeService 拆分而来（单文件 ≤300 行门禁），mapper 由宿主构造器传入。
 * 部门/岗位/用工类型筛选先经现行雇佣关系（status=1 且 effective_to 为空）收敛员工 ID，
 * 再回员工主表分页，避免跨表 join。</p>
 */
final class EmployeeRosterQuery {

    private final EmployeeMapper employeeMapper;
    private final EmploymentMapper employmentMapper;

    EmployeeRosterQuery(EmployeeMapper employeeMapper, EmploymentMapper employmentMapper) {
        this.employeeMapper = employeeMapper;
        this.employmentMapper = employmentMapper;
    }

    /** 花名册多维筛选 + 分页；雇佣维度无命中直接返回空页。 */
    PageResult<EmployeeBrief> roster(String name, String empNo, Integer status, Long deptId,
                                     Long positionId, String hireType, long page, long size) {
        List<Long> empIds = resolveEmpIdsByEmployment(deptId, positionId, hireType);
        if (empIds != null && empIds.isEmpty()) {
            return new PageResult<>(List.of(), page, size, 0);
        }
        Page<Employee> p = new Page<>(page, size);
        LambdaQueryWrapper<Employee> qw = new LambdaQueryWrapper<>();
        if (empIds != null) {
            qw.in(Employee::getId, empIds);
        }
        if (name != null && !name.isBlank()) {
            qw.like(Employee::getName, name);
        }
        if (empNo != null && !empNo.isBlank()) {
            qw.eq(Employee::getEmpNo, empNo);
        }
        if (status != null) {
            qw.eq(Employee::getStatus, status);
        }
        qw.orderByDesc(Employee::getId);
        Page<Employee> result = employeeMapper.selectPage(p, qw);

        Map<Long, Employment> empMap = batchCurrentEmployments(result.getRecords());
        List<EmployeeBrief> briefs = result.getRecords().stream()
                .map(e -> EmployeeProfileAssembler.toBrief(e, empMap.get(e.getId())))
                .toList();
        return new PageResult<>(briefs, result.getCurrent(), result.getSize(), result.getTotal());
    }

    /** 雇佣维度筛选：无任何雇佣条件时返回 null（表示不收敛）；有条件下无命中返回空集合。 */
    private List<Long> resolveEmpIdsByEmployment(Long deptId, Long positionId, String hireType) {
        if (deptId == null && positionId == null && (hireType == null || hireType.isBlank())) {
            return null;
        }
        LambdaQueryWrapper<Employment> ew = new LambdaQueryWrapper<Employment>()
                .eq(Employment::getStatus, 1)
                .isNull(Employment::getEffectiveTo);
        if (deptId != null) {
            ew.eq(Employment::getDeptId, deptId);
        }
        if (positionId != null) {
            ew.eq(Employment::getPositionId, positionId);
        }
        if (hireType != null && !hireType.isBlank()) {
            ew.eq(Employment::getHireType, hireType);
        }
        return employmentMapper.selectList(ew).stream().map(Employment::getEmpId).toList();
    }

    /** 批量取现行雇佣关系（同员工多行时取第一条）。 */
    private Map<Long, Employment> batchCurrentEmployments(List<Employee> rows) {
        if (rows.isEmpty()) {
            return Map.of();
        }
        List<Long> ids = rows.stream().map(Employee::getId).toList();
        List<Employment> list = employmentMapper.selectList(new LambdaQueryWrapper<Employment>()
                .in(Employment::getEmpId, ids)
                .eq(Employment::getStatus, 1)
                .isNull(Employment::getEffectiveTo));
        return list.stream().collect(Collectors.toMap(Employment::getEmpId, Function.identity(), (a, b) -> a));
    }
}
