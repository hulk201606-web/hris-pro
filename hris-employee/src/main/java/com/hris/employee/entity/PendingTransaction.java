package com.hris.employee.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.hris.common.entity.BaseEntity;

import java.time.LocalDate;

/**
 * 待生效事务队列（emp_pending_transaction，幂等落账）。payload 存事务载荷（JSON 字符串）。
 */
@TableName("emp_pending_transaction")
public class PendingTransaction extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 员工 ID（逻辑外键 emp_employee.id） */
    private Long empId;

    /** 事务流水号（幂等键，租户内唯一） */
    private String transNo;

    /** 事务类型 ONBOARD/REGULAR/TRANSFER/RESIGN/PROMOTE */
    private String transType;

    /** 生效日期 */
    private LocalDate effectDate;

    /** 事务载荷（JSON） */
    private String payload;

    /** 状态 PENDING/APPLIED/CANCELLED */
    private String state;

    public Long getEmpId() {
        return empId;
    }

    public void setEmpId(Long empId) {
        this.empId = empId;
    }

    public String getTransNo() {
        return transNo;
    }

    public void setTransNo(String transNo) {
        this.transNo = transNo;
    }

    public String getTransType() {
        return transType;
    }

    public void setTransType(String transType) {
        this.transType = transType;
    }

    public LocalDate getEffectDate() {
        return effectDate;
    }

    public void setEffectDate(LocalDate effectDate) {
        this.effectDate = effectDate;
    }

    public String getPayload() {
        return payload;
    }

    public void setPayload(String payload) {
        this.payload = payload;
    }

    public String getState() {
        return state;
    }

    public void setState(String state) {
        this.state = state;
    }
}
