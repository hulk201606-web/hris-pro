package com.hris.performance.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.hris.common.entity.BaseEntity;


/**
 * 校准快照（域：绩效，分层：实体/DO）。校准会议前后的评分快照 JSON，支持回溯与争议复盘。
 */
@TableName("prf_calibration_snapshot")
public class CalibrationSnapshot extends BaseEntity {

    private static final long serialVersionUID = 1L;
    private Long sessionId;  // 校准会议ID(逻辑外键 prf_calibration_session.id)

    public Long getSessionId() {
        return sessionId;
    }

    public void setSessionId(Long sessionId) {
        this.sessionId = sessionId;
    }
    private String snapshot;  // 校准快照(九宫格/调整结果)

    public String getSnapshot() {
        return snapshot;
    }

    public void setSnapshot(String snapshot) {
        this.snapshot = snapshot;
    }
}
