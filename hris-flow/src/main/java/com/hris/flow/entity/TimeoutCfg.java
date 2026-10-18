package com.hris.flow.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.hris.common.entity.BaseEntity;

/**
 * 节点超时催办/升级配置（flow_ext_timeout_cfg）。
 * 唯一约束 uk(tenant_id, proc_def_id, node_id)。
 */
@TableName("flow_ext_timeout_cfg")
public class TimeoutCfg extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 流程定义 ID（逻辑外键 ACT_RE_PROCDEF.ID_） */
    private String procDefId;

    /** 节点 ID（BPMN 元素 id） */
    private String nodeId;

    /** 催办 cron 表达式 */
    private String remindCron;

    /** 升级小时数（超时 n 小时自动跳级） */
    private Integer escalateAfterH;

    /** 升级目标用户 ID */
    private Long escalateTo;

    public String getProcDefId() {
        return procDefId;
    }

    public void setProcDefId(String procDefId) {
        this.procDefId = procDefId;
    }

    public String getNodeId() {
        return nodeId;
    }

    public void setNodeId(String nodeId) {
        this.nodeId = nodeId;
    }

    public String getRemindCron() {
        return remindCron;
    }

    public void setRemindCron(String remindCron) {
        this.remindCron = remindCron;
    }

    public Integer getEscalateAfterH() {
        return escalateAfterH;
    }

    public void setEscalateAfterH(Integer escalateAfterH) {
        this.escalateAfterH = escalateAfterH;
    }

    public Long getEscalateTo() {
        return escalateTo;
    }

    public void setEscalateTo(Long escalateTo) {
        this.escalateTo = escalateTo;
    }
}
