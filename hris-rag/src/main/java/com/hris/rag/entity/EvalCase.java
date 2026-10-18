package com.hris.rag.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.hris.common.entity.BaseEntity;


/**
 * kb_eval_case（由 DDL 自动生成）
 */
@TableName("kb_eval_case")
public class EvalCase extends BaseEntity {

    private static final long serialVersionUID = 1L;
    private Long evalSetId;  // 评测集ID(逻辑外键 kb_eval_set.id)

    public Long getEvalSetId() {
        return evalSetId;
    }

    public void setEvalSetId(Long evalSetId) {
        this.evalSetId = evalSetId;
    }
    private String question;  // 问题

    public String getQuestion() {
        return question;
    }

    public void setQuestion(String question) {
        this.question = question;
    }
    private String expectedRefs;  // 期望引用(json)

    public String getExpectedRefs() {
        return expectedRefs;
    }

    public void setExpectedRefs(String expectedRefs) {
        this.expectedRefs = expectedRefs;
    }
    private String groundTruth;  // 标准答案

    public String getGroundTruth() {
        return groundTruth;
    }

    public void setGroundTruth(String groundTruth) {
        this.groundTruth = groundTruth;
    }
}
