package com.hris.employee.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hris.employee.entity.Employment;
import com.hris.employee.mapper.EmploymentMapper;
import org.springframework.stereotype.Service;

import java.time.LocalDate;

/**
 * 雇佣关系版本链（emp_employment，核心，docs/06 §3 / docs/12 §5.3）。
 * 现行雇佣关系 = status=1 且 effective_to IS NULL。
 */
@Service
public class EmploymentService {

    private final EmploymentMapper employmentMapper;

    public EmploymentService(EmploymentMapper employmentMapper) {
        this.employmentMapper = employmentMapper;
    }

    /** 当前有效雇佣关系。 */
    public Employment current(Long empId) {
        return employmentMapper.selectOne(new LambdaQueryWrapper<Employment>()
                .eq(Employment::getEmpId, empId)
                .eq(Employment::getStatus, 1)
                .isNull(Employment::getEffectiveTo));
    }

    /** 关闭现行雇佣关系（effective_to = 结束日，status=0）。 */
    public void closeCurrent(Long empId, LocalDate effectiveTo) {
        Employment cur = current(empId);
        if (cur == null) {
            return;
        }
        cur.setStatus(0);
        cur.setEffectiveTo(effectiveTo);
        employmentMapper.updateById(cur);
    }

    /**
     * 开启新雇佣关系版本。
     *
     * <p>同日幂等合并：uk_emp_employment_eff(tenant, emp, effective_from) 约束同一员工
     * 同一生效日仅一条雇佣行——入职当日即调动（同日 close+open）是合法业务，直接
     * insert 会撞唯一键（docs/19 §9.2 一期验收 e2e 实测）。查到同日行则就地更新
     * 雇佣事实（部门/岗位等覆盖为最新），否则新插一行。</p>
     */
    public void open(Long empId, Long legalEntityId, Long deptId, Long positionId, Long gradeId,
                     String hireType, Long supervisorEmpId, LocalDate effectiveFrom) {
        Employment sameDay = employmentMapper.selectOne(new LambdaQueryWrapper<Employment>()
                .eq(Employment::getEmpId, empId)
                .eq(Employment::getEffectiveFrom, effectiveFrom)
                .last("limit 1"));
        if (sameDay != null) {
            sameDay.setLegalEntityId(legalEntityId);
            sameDay.setDeptId(deptId);
            sameDay.setPositionId(positionId);
            sameDay.setGradeId(gradeId);
            sameDay.setHireType(hireType);
            sameDay.setSupervisorEmpId(supervisorEmpId);
            sameDay.setStatus(1);
            employmentMapper.updateById(sameDay);
            return;
        }
        Employment e = new Employment();
        e.setEmpId(empId);
        e.setLegalEntityId(legalEntityId);
        e.setDeptId(deptId);
        e.setPositionId(positionId);
        e.setGradeId(gradeId);
        e.setHireType(hireType);
        e.setSupervisorEmpId(supervisorEmpId);
        e.setEffectiveFrom(effectiveFrom);
        e.setStatus(1);
        employmentMapper.insert(e);
    }
}
