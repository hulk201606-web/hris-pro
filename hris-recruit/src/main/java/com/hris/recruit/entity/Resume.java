package com.hris.recruit.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.hris.common.entity.BaseEntity;


/**
 * rcu_resume（由 DDL 自动生成）
 */
@TableName("rcu_resume")
public class Resume extends BaseEntity {

    private static final long serialVersionUID = 1L;
    private Long candidateId;  // 候选人ID(逻辑外键 rcu_candidate.id)

    public Long getCandidateId() {
        return candidateId;
    }

    public void setCandidateId(Long candidateId) {
        this.candidateId = candidateId;
    }
    private Long fileId;  // 简历附件ID(逻辑外键 sys_file_meta.id)

    public Long getFileId() {
        return fileId;
    }

    public void setFileId(Long fileId) {
        this.fileId = fileId;
    }
    private String parseResult;  // 简历解析结果(json)

    public String getParseResult() {
        return parseResult;
    }

    public void setParseResult(String parseResult) {
        this.parseResult = parseResult;
    }
}
