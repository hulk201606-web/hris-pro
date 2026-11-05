package com.hris.org.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.hris.common.entity.BaseEntity;

import java.time.LocalDate;

/**
 * 部门（树 + 版本化生效）。path 为物化路径（如 1.12.130）。
 * 历史版本落 org_dept_history 快照，当前表仅保留现行行（code 唯一）。
 */
@TableName("org_dept")
public class OrgDept extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 父部门 ID，0=根 */
    private Long parentId;

    /** 物化路径 */
    private String path;

    /** 部门名称 */
    private String name;

    /** 部门编码（租户内唯一） */
    private String code;

    /** 部门负责人员工 ID（逻辑外键 emp_employee.id） */
    private Long managerEmpId;

    /** 生效开始日 */
    private LocalDate effectiveFrom;

    /** 生效结束日（NULL=现行） */
    private LocalDate effectiveTo;

    /** 状态 1=有效 0=失效 */
    private Integer status;

    /** 兄弟排序号（ORG-F-002 深化拖拽排序，0..n-1 连续重排） */
    private Integer sortOrder;

    public Integer getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(Integer sortOrder) {
        this.sortOrder = sortOrder;
    }

    public Long getParentId() {
        return parentId;
    }

    public void setParentId(Long parentId) {
        this.parentId = parentId;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public Long getManagerEmpId() {
        return managerEmpId;
    }

    public void setManagerEmpId(Long managerEmpId) {
        this.managerEmpId = managerEmpId;
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
