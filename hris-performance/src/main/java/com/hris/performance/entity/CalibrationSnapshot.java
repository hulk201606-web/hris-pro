package com.hris.performance.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.hris.common.entity.BaseEntity;


/**
 * prf_calibration_snapshot（由 DDL 自动生成）
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
