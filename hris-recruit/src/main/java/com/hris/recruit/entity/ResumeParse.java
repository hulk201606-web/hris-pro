package com.hris.recruit.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.hris.common.entity.BaseEntity;

/**
 * rcu_resume_parse（RCS-F-007）：简历解析结果，一简历一份最新解析（uk tenant_id+resume_id）。
 */
@TableName("rcu_resume_parse")
public class ResumeParse extends BaseEntity {

    private static final long serialVersionUID = 1L;
    private Long resumeId;  // 简历ID(逻辑外键 rcu_resume.id)

    public Long getResumeId() {
        return resumeId;
    }

    public void setResumeId(Long resumeId) {
        this.resumeId = resumeId;
    }
    private Long candidateId;  // 候选人ID(逻辑外键 rcu_candidate.id)

    public Long getCandidateId() {
        return candidateId;
    }

    public void setCandidateId(Long candidateId) {
        this.candidateId = candidateId;
    }
    private String parsedName;  // 解析姓名(启发式,需人工核对)

    public String getParsedName() {
        return parsedName;
    }

    public void setParsedName(String parsedName) {
        this.parsedName = parsedName;
    }
    private String parsedMobile;  // 解析手机号(大陆11位)

    public String getParsedMobile() {
        return parsedMobile;
    }

    public void setParsedMobile(String parsedMobile) {
        this.parsedMobile = parsedMobile;
    }
    private String parsedEmail;  // 解析邮箱

    public String getParsedEmail() {
        return parsedEmail;
    }

    public void setParsedEmail(String parsedEmail) {
        this.parsedEmail = parsedEmail;
    }
    private String educationJson;  // 教育经历(json数组)

    public String getEducationJson() {
        return educationJson;
    }

    public void setEducationJson(String educationJson) {
        this.educationJson = educationJson;
    }
    private String experienceJson;  // 工作/项目经历(json数组)

    public String getExperienceJson() {
        return experienceJson;
    }

    public void setExperienceJson(String experienceJson) {
        this.experienceJson = experienceJson;
    }
    private String parseStatus;  // 解析状态 OK/RULE_LIMITED(规则解析,需人工核对)

    public String getParseStatus() {
        return parseStatus;
    }

    public void setParseStatus(String parseStatus) {
        this.parseStatus = parseStatus;
    }
    private String parseError;  // 解析错误说明

    public String getParseError() {
        return parseError;
    }

    public void setParseError(String parseError) {
        this.parseError = parseError;
    }
}
