package com.hris.performance.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.hris.common.entity.BaseEntity;

import java.time.LocalDate;

/**
 * 绩效面谈（域：绩效，分层：实体/DO）。面谈记录 + PIP 跟踪，conclusion=PIP 时必填
 * pip 起止日且 pip_status 由服务端状态机维护（缺省 ONGOING，仅经 pip-result 迁移）。
 */
@TableName("prf_interview")
public class Interview extends BaseEntity {

    private static final long serialVersionUID = 1L;
    private Long cycleId;  // 周期ID(逻辑外键 prf_cycle.id)
    private Long empId;  // 员工ID(逻辑外键 emp_employee.id)
    private Integer round;  // 面谈轮次(同周期同员工唯一，默认1)
    private LocalDate interviewDate;  // 面谈日期
    private Long interviewerEmpId;  // 面谈人员工ID(逻辑外键 emp_employee.id)
    private String summary;  // 面谈纪要
    private String conclusion;  // 结论 IMPROVE/KEEP/PROMOTE/PIP
    private LocalDate pipStartDate;  // PIP开始日
    private LocalDate pipEndDate;  // PIP结束日
    private String pipStatus;  // PIP状态 ONGOING/PASSED/FAILED(非 PIP 结论为 NULL)

    public Long getCycleId() {
        return cycleId;
    }

    public void setCycleId(Long cycleId) {
        this.cycleId = cycleId;
    }

    public Long getEmpId() {
        return empId;
    }

    public void setEmpId(Long empId) {
        this.empId = empId;
    }

    public Integer getRound() {
        return round;
    }

    public void setRound(Integer round) {
        this.round = round;
    }

    public LocalDate getInterviewDate() {
        return interviewDate;
    }

    public void setInterviewDate(LocalDate interviewDate) {
        this.interviewDate = interviewDate;
    }

    public Long getInterviewerEmpId() {
        return interviewerEmpId;
    }

    public void setInterviewerEmpId(Long interviewerEmpId) {
        this.interviewerEmpId = interviewerEmpId;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public String getConclusion() {
        return conclusion;
    }

    public void setConclusion(String conclusion) {
        this.conclusion = conclusion;
    }

    public LocalDate getPipStartDate() {
        return pipStartDate;
    }

    public void setPipStartDate(LocalDate pipStartDate) {
        this.pipStartDate = pipStartDate;
    }

    public LocalDate getPipEndDate() {
        return pipEndDate;
    }

    public void setPipEndDate(LocalDate pipEndDate) {
        this.pipEndDate = pipEndDate;
    }

    public String getPipStatus() {
        return pipStatus;
    }

    public void setPipStatus(String pipStatus) {
        this.pipStatus = pipStatus;
    }
}
