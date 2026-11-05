package com.hris.rag.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 智能问答配置（KBD-F-008/011）。
 */
@ConfigurationProperties(prefix = "hris.kb.chat")
public class ChatProperties {

    /**
     * 无召回拒答阈值：最高 COSINE 相似度低于该值视为知识未收录。
     * 依据 text-embedding-v4 实测分布校准：相关问题约 0.60+，无关问题 ≤0.40，
     * 故取 0.5（初版 0.35 会放过语义无关但词汇沾边的查询）。
     */
    private double recallThreshold = 0.5;
    /** 送入 LLM 的知识片段数（Top-K） */
    private int topK = 5;
    /** 向量检索候选条数（先多取再按可见空间过滤裁剪） */
    private int candidateTopK = 10;
    /** 多轮对话携带的历史消息条数（KBD-F-009） */
    private int historyTurns = 8;
    /** LLM 调用超时（秒） */
    private int timeoutSec = 30;

    public double getRecallThreshold() {
        return recallThreshold;
    }

    public void setRecallThreshold(double recallThreshold) {
        this.recallThreshold = recallThreshold;
    }

    public int getTopK() {
        return topK;
    }

    public void setTopK(int topK) {
        this.topK = topK;
    }

    public int getCandidateTopK() {
        return candidateTopK;
    }

    public void setCandidateTopK(int candidateTopK) {
        this.candidateTopK = candidateTopK;
    }

    public int getHistoryTurns() {
        return historyTurns;
    }

    public void setHistoryTurns(int historyTurns) {
        this.historyTurns = historyTurns;
    }

    public int getTimeoutSec() {
        return timeoutSec;
    }

    public void setTimeoutSec(int timeoutSec) {
        this.timeoutSec = timeoutSec;
    }
}
