package com.hris.compensation.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.hris.common.entity.BaseEntity;
import java.time.LocalDateTime;

/**
 * 工资单（域：薪酬，分层：实体/DO，密域）。发放给员工的月度工资单，netPayCipher 为实发金额密文，非授权视图返回 netPayMask；发送状态 UNSENT→SENT。
 */
@TableName("cmp_payslip")
public class Payslip extends BaseEntity {

    private static final long serialVersionUID = 1L;
    private Long runId;  // 算薪批次ID(逻辑外键 cmp_payroll_run.id)

    public Long getRunId() {
        return runId;
    }

    public void setRunId(Long runId) {
        this.runId = runId;
    }
    private Long empId;  // 员工ID(逻辑外键 emp_employee.id)

    public Long getEmpId() {
        return empId;
    }

    public void setEmpId(Long empId) {
        this.empId = empId;
    }
    private String netPayCipher;  // 实发净额密文(逻辑列 net_pay)

    public String getNetPayCipher() {
        return netPayCipher;
    }

    public void setNetPayCipher(String netPayCipher) {
        this.netPayCipher = netPayCipher;
    }
    private String netPayMask;  // 实发净额掩码

    public String getNetPayMask() {
        return netPayMask;
    }

    public void setNetPayMask(String netPayMask) {
        this.netPayMask = netPayMask;
    }
    private String sendStatus;  // 发放状态 PENDING/SENT/VIEWED

    public String getSendStatus() {
        return sendStatus;
    }

    public void setSendStatus(String sendStatus) {
        this.sendStatus = sendStatus;
    }
    private LocalDateTime sentAt;  // 发送时间(UTC)

    public LocalDateTime getSentAt() {
        return sentAt;
    }

    public void setSentAt(LocalDateTime sentAt) {
        this.sentAt = sentAt;
    }
}
