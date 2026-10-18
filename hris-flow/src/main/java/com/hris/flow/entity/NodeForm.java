package com.hris.flow.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.hris.common.entity.BaseEntity;

/**
 * 节点级表单绑定（flow_ext_node_form）。
 * 唯一约束 uk(tenant_id, proc_def_id, node_id)。
 */
@TableName("flow_ext_node_form")
public class NodeForm extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 流程定义 ID（逻辑外键 ACT_RE_PROCDEF.ID_） */
    private String procDefId;

    /** 节点 ID（BPMN 元素 id） */
    private String nodeId;

    /** 节点表单 JSON Schema */
    private String formSchemaJson;

    /** 只读表达式（字段权限表达式） */
    private String readonlyExpr;

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

    public String getFormSchemaJson() {
        return formSchemaJson;
    }

    public void setFormSchemaJson(String formSchemaJson) {
        this.formSchemaJson = formSchemaJson;
    }

    public String getReadonlyExpr() {
        return readonlyExpr;
    }

    public void setReadonlyExpr(String readonlyExpr) {
        this.readonlyExpr = readonlyExpr;
    }
}
