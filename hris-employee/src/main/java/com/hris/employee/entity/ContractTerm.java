package com.hris.employee.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.hris.common.entity.BaseEntity;

import java.time.LocalDate;

/**
 * 合同条款明细（emp_contract_term，EMP-F-014 深化）。
 *
 * <p>合同台账的条款级补充：试用/薪酬/保密/竞业/自定义条款，含生效区间。
 * 逻辑外键 emp_contract.id，随租户插件隔离。</p>
 */
@TableName("emp_contract_term")
public class ContractTerm extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 合同 ID（逻辑外键 emp_contract.id） */
    private Long contractId;

    /** 条款类型 PROBATION/SALARY/CONFIDENTIALITY/NON_COMPETE/CUSTOM */
    private String termType;

    /** 条款内容 */
    private String content;

    /** 生效日 */
    private LocalDate effectiveFrom;

    /** 失效日（NULL=长期有效） */
    private LocalDate effectiveTo;

    public Long getContractId() {
        return contractId;
    }

    public void setContractId(Long contractId) {
        this.contractId = contractId;
    }

    public String getTermType() {
        return termType;
    }

    public void setTermType(String termType) {
        this.termType = termType;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
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
}
