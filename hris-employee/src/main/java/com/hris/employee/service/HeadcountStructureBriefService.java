package com.hris.employee.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hris.employee.dto.ReportDtos.HeadcountStructureBriefRow;
import com.hris.employee.entity.Employment;
import com.hris.employee.mapper.EmploymentMapper;
import com.hris.employee.entity.Employee;
import com.hris.employee.mapper.EmployeeMapper;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 人力结构简报服务（P1 报表数据源，内部端点专用）。
 *
 * <p>口径：以员工主档全集为基（含离职等终态，供「按状态」维度），
 * 左连现行雇佣关系（status=1 且 effective_to IS NULL）取部门/雇佣类型/法人；
 * 指定 asOf 时取该时点有效雇佣版本（effective_from <= asOf < effective_to 闭开区间）。</p>
 */
@Service
public class HeadcountStructureBriefService {

    private final EmploymentMapper employmentMapper;
    private final EmployeeMapper employeeMapper;

    public HeadcountStructureBriefService(EmploymentMapper employmentMapper, EmployeeMapper employeeMapper) {
        this.employmentMapper = employmentMapper;
        this.employeeMapper = employeeMapper;
    }

    /**
     * 批量返回员工×雇佣关系简报行（组织维度字段，无敏感信息）。
     */
    public List<HeadcountStructureBriefRow> rows(LocalDate asOf) {
        // 员工主档全集（含离职终态，按状态维度需覆盖全部员工）
        List<Employee> employees = employeeMapper.selectList(new LambdaQueryWrapper<>());
        if (employees.isEmpty()) {
            return List.of();
        }

        // 雇佣关系：现行 or asOf 时点版本
        LambdaQueryWrapper<Employment> qw = new LambdaQueryWrapper<Employment>()
                .eq(Employment::getStatus, 1);
        if (asOf == null) {
            qw.isNull(Employment::getEffectiveTo);
        } else {
            qw.le(Employment::getEffectiveFrom, asOf)
                    .and(w -> w.isNull(Employment::getEffectiveTo).or().ge(Employment::getEffectiveTo, asOf));
        }
        List<Employment> employments = employmentMapper.selectList(qw);
        Map<Long, Employment> currentByEmp = new HashMap<>();
        for (Employment e : employments) {
            currentByEmp.putIfAbsent(e.getEmpId(), e);
        }

        Set<Long> linked = new HashSet<>();
        List<HeadcountStructureBriefRow> rows = new java.util.ArrayList<>();
        for (Employee emp : employees) {
            Employment em = currentByEmp.get(emp.getId());
            if (em != null) {
                linked.add(emp.getId());
            }
            rows.add(new HeadcountStructureBriefRow(emp.getId(),
                    em == null ? null : em.getLegalEntityId(),
                    em == null ? null : em.getDeptId(),
                    em == null ? null : em.getHireType(),
                    emp.getStatus()));
        }
        // 有现行雇佣但主档缺失的脏数据行也返回（empId 兜底），保证聚合不丢人头
        for (Employment em : employments) {
            if (!linked.contains(em.getEmpId())) {
                rows.add(new HeadcountStructureBriefRow(em.getEmpId(), em.getLegalEntityId(),
                        em.getDeptId(), em.getHireType(), null));
            }
        }
        return rows;
    }
}
