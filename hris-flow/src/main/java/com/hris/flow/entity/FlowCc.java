package com.hris.flow.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.hris.common.entity.BaseEntity;

/**
 * 审批抄送记录（flow_ext_cc，FLW-F-011）。
 * 唯一约束 uk(tenant_id, proc_inst_id, cc_node, emp_id) 防重复抄送。
 */
@TableName("flow_ext_cc")
public class FlowCc extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 流程实例 ID（逻辑外键 ACT_HI_PROCINST.ID_） */
    private String procInstId;

    /** 抄送发起节点 ID（BPMN userTask id，抄送写入时所在节点） */
    private String ccNode;

    /** 被抄送人用户 ID（逻辑外键 auth_user.id） */
    private Long empId;

    public String getProcInstId() {
        return procInstId;
    }

    public void setProcInstId(String procInstId) {
        this.procInstId = procInstId;
    }

    public String getCcNode() {
        return ccNode;
    }

    public void setCcNode(String ccNode) {
        this.ccNode = ccNode;
    }

    public Long getEmpId() {
        return empId;
    }

    public void setEmpId(Long empId) {
        this.empId = empId;
    }
}
