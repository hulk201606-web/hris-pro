package com.hris.employee.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.hris.common.entity.BaseEntity;

import java.time.LocalDate;

/**
 * 雇佣关系版本链（emp_employment，核心）。现行雇佣关系 = status=1 且 effective_to IS NULL。
 */
@TableName("emp_employment")
public class Employment extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 员工 ID（逻辑外键 emp_employee.id） */
    private Long empId;

    /** 法人 ID（逻辑外键 org_legal_entity.id） */
    private Long legalEntityId;

    /** 部门 ID（逻辑外键 org_dept.id） */
    private Long deptId;

    /** 岗位 ID（逻辑外键 org_position.id） */
    private Long positionId;

    /** 职级 ID（逻辑外键 org_grade.id） */
    private Long gradeId;

    /** 雇佣类型 FULLTIME/PARTTIME/INTERN/CONSULTANT */
    private String hireType;

    /** 直属上级员工 ID（逻辑外键 emp_employee.id） */
    private Long supervisorEmpId;

    /** 生效开始日 */
    private LocalDate effectiveFrom;

    /** 生效结束日（NULL=现行） */
    private LocalDate effectiveTo;

    /** 状态 1=有效 0=失效 */
    private Integer status;

    public Long getEmpId() {
        return empId;
    }

    public void setEmpId(Long empId) {
        this.empId = empId;
    }

    public Long getLegalEntityId() {
        return legalEntityId;
    }

    public void setLegalEntityId(Long legalEntityId) {
        this.legalEntityId = legalEntityId;
    }

    public Long getDeptId() {
        return deptId;
    }

    public void setDeptId(Long deptId) {
        this.deptId = deptId;
    }

    public Long getPositionId() {
        return positionId;
    }

    public void setPositionId(Long positionId) {
        this.positionId = positionId;
    }

    public Long getGradeId() {
        return gradeId;
    }

    public void setGradeId(Long gradeId) {
        this.gradeId = gradeId;
    }

    public String getHireType() {
        return hireType;
    }

    public void setHireType(String hireType) {
        this.hireType = hireType;
    }

    public Long getSupervisorEmpId() {
        return supervisorEmpId;
    }

    public void setSupervisorEmpId(Long supervisorEmpId) {
        this.supervisorEmpId = supervisorEmpId;
    }

    public LocalDate getEffectiveFrom() {
        return effectiveFrom;
    }

    public void setEffectiveFrom(LocalDate effectiveFrom) {
        this.effectiveFrom = effectiveFrom;
    }

    public LocalDate getEffectiveTo() {
        return effectiveTo;
    }

    public void setEffectiveTo(LocalDate effectiveTo) {
        this.effectiveTo = effectiveTo;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }
}
