package com.hris.rag.service;

import com.hris.rag.entity.Chunk;
import com.hris.rag.entity.QaMessage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ChatPromptBuilder 单元测试：system 约束、来源编号、多轮历史拼装、拒答文案。
 */
class ChatPromptBuilderTest {

    private Chunk chunk(String text) {
        Chunk c = new Chunk();
        c.setId(400001L);
        c.setText(text);
        return c;
    }

    @Test
    @DisplayName("system prompt：约束仅依据片段回答并标注来源编号")
    void systemPrompt_containsConstraints() {
        assertTrue(ChatPromptBuilder.SYSTEM_PROMPT.contains("仅依据提供的知识片段回答"));
        assertTrue(ChatPromptBuilder.SYSTEM_PROMPT.contains("标注来源编号"));
        assertTrue(ChatPromptBuilder.SYSTEM_PROMPT.contains("不知道"));
    }

    @Test
    @DisplayName("user prompt：片段带编号与来源标题，含当前问题")
    void userPrompt_numbersSources() {
        String p = ChatPromptBuilder.buildUserPrompt(
                List.of(), List.of(chunk("年假 15 天")),
                Map.of(400001L, "年假管理制度"), "年假有多少天");
        assertTrue(p.contains("[1] 来源: 年假管理制度"));
        assertTrue(p.contains("年假 15 天"));
        assertTrue(p.contains("年假有多少天"));
    }

    @Test
    @DisplayName("多轮历史拼进 user prompt（KBD-F-009）")
    void userPrompt_includesHistory() {
        QaMessage m = new QaMessage();
        m.setQuestion("探亲假有几天");
        m.setAnswer("20 天 [1]");
        String p = ChatPromptBuilder.buildUserPrompt(
                List.of(m), List.of(), Map.of(), "那路费报销吗");
        assertTrue(p.contains("用户: 探亲假有几天"));
        assertTrue(p.contains("助手: 20 天 [1]"));
        assertTrue(p.contains("那路费报销吗"));
    }

    @Test
    @DisplayName("无片段时知识片段段位标注（无）")
    void userPrompt_emptyChunks() {
        String p = ChatPromptBuilder.buildUserPrompt(List.of(), List.of(), Map.of(), "q");
        assertTrue(p.contains("（无）"));
    }

    @Test
    @DisplayName("拒答文案：固定开头，无标题时不列文档")
    void noRecallAnswer_withoutTitles() {
        String p = ChatPromptBuilder.buildNoRecallAnswer(List.of());
        assertEquals("知识库未收录该问题的相关信息，请换个说法或联系管理员补充知识", p);
        assertFalse(p.contains("最接近的文档"));
    }

    @Test
    @DisplayName("拒答文案：列出最接近文档标题")
    void noRecallAnswer_withTitles() {
        String p = ChatPromptBuilder.buildNoRecallAnswer(List.of("年假管理制度", "考勤说明"));
        assertTrue(p.contains("《年假管理制度》"));
        assertTrue(p.contains("《考勤说明》"));
    }
}
