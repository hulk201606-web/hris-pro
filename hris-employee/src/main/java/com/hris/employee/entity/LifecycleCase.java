package com.hris.employee.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.hris.common.entity.BaseEntity;

import java.time.LocalDate;

/**
 * 生命周期单（emp_lifecycle_case，入转调离）。payload 存单据业务数据（JSON 字符串）。
 */
@TableName("emp_lifecycle_case")
public class LifecycleCase extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 员工 ID（逻辑外键 emp_employee.id） */
    private Long empId;

    /** 类型 ONBOARD/REGULAR/TRANSFER/RESIGN */
    private String type;

    /** 业务单号（流程幂等键，租户内唯一） */
    private String bizKey;

    /** 单据业务数据（JSON） */
    private String payload;

    /** 流程实例 ID（逻辑外键 flow_ext_biz_relation.proc_inst_id） */
    private String procInstId;

    /** 状态 DRAFT/PROCESSING/DONE/CANCELLED */
    private String state;

    /** 生效日期 */
    private LocalDate effectDate;

    public Long getEmpId() {
        return empId;
    }

    public void setEmpId(Long empId) {
        this.empId = empId;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getBizKey() {
        return bizKey;
    }

    public void setBizKey(String bizKey) {
        this.bizKey = bizKey;
    }

    public String getPayload() {
        return payload;
    }

    public void setPayload(String payload) {
        this.payload = payload;
    }

    public String getProcInstId() {
        return procInstId;
    }

    public void setProcInstId(String procInstId) {
        this.procInstId = procInstId;
    }

    public String getState() {
        return state;
    }

    public void setState(String state) {
        this.state = state;
    }

    public LocalDate getEffectDate() {
        return effectDate;
    }

    public void setEffectDate(LocalDate effectDate) {
        this.effectDate = effectDate;
    }
}
