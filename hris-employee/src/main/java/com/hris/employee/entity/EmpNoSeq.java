package com.hris.employee.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.hris.common.entity.BaseEntity;

/**
 * 工号序号段（emp_no_seq，EMP-F-002）。
 *
 * <p>uk(tenant_id, prefix, seq_year) 保证租户+前缀+年度只有一行；分配序号时对该行
 * 加行锁（SELECT ... FOR UPDATE），按年归零。前缀取模板 {PREFIX} 渲染后的值，
 * 保证不同前缀的工号各自独立递增。</p>
 */
@TableName("emp_no_seq")
public class EmpNoSeq extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 工号前缀 */
    private String prefix;

    /** 序号年度（按年重置） */
    private Integer seqYear;

    /** 已分配最大序号 */
    private Integer currentSeq;

    public String getPrefix() {
        return prefix;
    }

    public void setPrefix(String prefix) {
        this.prefix = prefix;
    }

    public Integer getSeqYear() {
        return seqYear;
    }

    public void setSeqYear(Integer seqYear) {
        this.seqYear = seqYear;
    }

    public Integer getCurrentSeq() {
        return currentSeq;
    }

    public void setCurrentSeq(Integer currentSeq) {
        this.currentSeq = currentSeq;
    }
}
