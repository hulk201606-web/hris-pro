package com.hris.rag.service;

import com.hris.rag.entity.Chunk;
import com.hris.rag.entity.QaMessage;

import java.util.List;

/**
 * 问答 Prompt 组装（KBD-F-008/009）。独立于 ChatService 便于单测。
 */
public final class ChatPromptBuilder {

    /** 系统提示词：约束仅依据知识片段回答并标注来源编号 */
    public static final String SYSTEM_PROMPT = """
            你是企业知识库助手。仅依据提供的知识片段回答；回答需标注来源编号如 [1]；\
            片段不足以回答时明确说明不知道。""";

    /** 无召回固定拒答文案（KBD-F-011，不得编造） */
    public static final String NO_RECALL_MESSAGE =
            "知识库未收录该问题的相关信息，请换个说法或联系管理员补充知识";

    private ChatPromptBuilder() {
    }

    /**
     * 组装用户消息：对话历史（多轮）+ 知识片段（编号 + 来源）+ 当前问题。
     *
     * @param history 最近 N 条历史消息（按时间升序）
     * @param chunks  命中切片（与标题对齐，序号即来源编号）
     * @param titles  chunkId -> 文档标题
     * @param question 当前问题
     */
    public static String buildUserPrompt(List<QaMessage> history, List<Chunk> chunks,
                                         java.util.Map<Long, String> titles, String question) {
        StringBuilder sb = new StringBuilder();
        if (!history.isEmpty()) {
            sb.append("【对话历史】\n");
            for (QaMessage m : history) {
                if (m.getQuestion() != null && !m.getQuestion().isBlank()) {
                    sb.append("用户: ").append(m.getQuestion()).append('\n');
                }
                if (m.getAnswer() != null && !m.getAnswer().isBlank()) {
                    sb.append("助手: ").append(m.getAnswer()).append('\n');
                }
            }
            sb.append('\n');
        }
        sb.append("【知识片段】\n");
        if (chunks.isEmpty()) {
            sb.append("（无）\n");
        } else {
            for (int i = 0; i < chunks.size(); i++) {
                Chunk c = chunks.get(i);
                sb.append('[').append(i + 1).append("] 来源: ")
                        .append(titles.getOrDefault(c.getId(), "未知文档"))
                        .append("\n").append(c.getText()).append("\n\n");
            }
        }
        sb.append("【问题】\n").append(question == null ? "" : question);
        return sb.toString();
    }

    /** 拒答回答：固定文案 + 最接近的文档标题提示（不含内容，避免越权暴露）。 */
    public static String buildNoRecallAnswer(List<String> nearestTitles) {
        StringBuilder sb = new StringBuilder(NO_RECALL_MESSAGE);
        if (!nearestTitles.isEmpty()) {
            sb.append("与您的问题最接近的文档：");
            for (int i = 0; i < nearestTitles.size(); i++) {
                if (i > 0) {
                    sb.append('、');
                }
                sb.append('《').append(nearestTitles.get(i)).append('》');
            }
        }
        return sb.toString();
    }
}
