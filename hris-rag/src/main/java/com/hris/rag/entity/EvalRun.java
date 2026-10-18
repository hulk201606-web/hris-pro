package com.hris.rag.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.hris.common.entity.BaseEntity;


/**
 * kb_eval_run（由 DDL 自动生成）
 */
@TableName("kb_eval_run")
public class EvalRun extends BaseEntity {

    private static final long serialVersionUID = 1L;
    private Long evalSetId;  // 评测集ID(逻辑外键 kb_eval_set.id)

    public Long getEvalSetId() {
        return evalSetId;
    }

    public void setEvalSetId(Long evalSetId) {
        this.evalSetId = evalSetId;
    }
    private String metrics;  // 指标快照(json)

    public String getMetrics() {
        return metrics;
    }

    public void setMetrics(String metrics) {
        this.metrics = metrics;
    }
    private String status;  // 状态 RUNNING/DONE/FAILED

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
