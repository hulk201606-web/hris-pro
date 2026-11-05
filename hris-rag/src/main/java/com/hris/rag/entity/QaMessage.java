package com.hris.rag.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.hris.common.entity.BaseEntity;

/**
 * qa_message（问答消息，KBD-F-008，由 DDL 自动生成）
 */
@TableName("qa_message")
public class QaMessage extends BaseEntity {

    private static final long serialVersionUID = 1L;
    private Long sessionId;  // 会话ID(逻辑外键 qa_session.id)
    private String role;  // 角色 USER/ASSISTANT
    private String question;  // 问题(用户消息)
    private String answer;  // 回答(助手消息)
    private String refs;  // 引用列表(json)
    private String toolCalls;  // 工具调用(json)
    private Integer latencyMs;  // 响应延迟(ms)
    private String model;  // 模型标识
    private String answerStatus;  // 回答状态 ANSWERED/NO_RECALL

    public Long getSessionId() {
        return sessionId;
    }

    public void setSessionId(Long sessionId) {
        this.sessionId = sessionId;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public String getQuestion() {
        return question;
    }

    public void setQuestion(String question) {
        this.question = question;
    }

    public String getAnswer() {
        return answer;
    }

    public void setAnswer(String answer) {
        this.answer = answer;
    }

    public String getRefs() {
        return refs;
    }

    public void setRefs(String refs) {
        this.refs = refs;
    }

    public String getToolCalls() {
        return toolCalls;
    }

    public void setToolCalls(String toolCalls) {
        this.toolCalls = toolCalls;
    }

    public Integer getLatencyMs() {
        return latencyMs;
    }

    public void setLatencyMs(Integer latencyMs) {
        this.latencyMs = latencyMs;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public String getAnswerStatus() {
        return answerStatus;
    }

    public void setAnswerStatus(String answerStatus) {
        this.answerStatus = answerStatus;
    }
}
