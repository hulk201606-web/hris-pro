package com.hris.employee.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.hris.common.entity.BaseEntity;

import java.time.LocalDate;

/**
 * 合同台账（emp_contract）。
 */
@TableName("emp_contract")
public class Contract extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 员工 ID（逻辑外键 emp_employee.id） */
    private Long empId;

    /** 合同编号（租户内唯一） */
    private String contractNo;

    /** 合同类型 FIXED/OPEN_ENDED/INTERN */
    private String type;

    /** 合同开始日 */
    private LocalDate startDate;

    /** 合同结束日（无固定期限为 NULL） */
    private LocalDate endDate;

    /** 试用期结束日 */
    private LocalDate probationEnd;

    /** 续签次数 */
    private Integer renewCnt;

    /** 合同附件 ID（逻辑外键 sys_file_meta.id） */
    private Long fileId;

    /** 状态 1=有效 0=终止 */
    private Integer status;

    public Long getEmpId() {
        return empId;
    }

    public void setEmpId(Long empId) {
        this.empId = empId;
    }

    public String getContractNo() {
        return contractNo;
    }

    public void setContractNo(String contractNo) {
        this.contractNo = contractNo;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public void setStartDate(LocalDate startDate) {
        this.startDate = startDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public void setEndDate(LocalDate endDate) {
        this.endDate = endDate;
    }

    public LocalDate getProbationEnd() {
        return probationEnd;
    }

    public void setProbationEnd(LocalDate probationEnd) {
        this.probationEnd = probationEnd;
    }

    public Integer getRenewCnt() {
        return renewCnt;
    }

    public void setRenewCnt(Integer renewCnt) {
        this.renewCnt = renewCnt;
    }

    public Long getFileId() {
        return fileId;
    }

    public void setFileId(Long fileId) {
        this.fileId = fileId;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }
}
