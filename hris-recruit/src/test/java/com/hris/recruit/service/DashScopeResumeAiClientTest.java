package com.hris.recruit.service;

import com.hris.common.exception.BizException;
import com.hris.recruit.config.RecruitProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * DashScopeResumeAiClient 单元测试（批 6 #9）：prompt 组装 / 响应解析（含 markdown 围栏）/
 * 失败语义（key 未配置、非法 JSON 均报错不静默降级）。LLM 网络调用由 E2E 真调覆盖。
 */
class DashScopeResumeAiClientTest {

    @Test
    @DisplayName("buildMessages：system 结构化抽取提示词 + user 携带简历文本")
    void buildMessagesAssemblesPrompt() {
        List<Map<String, String>> messages = DashScopeResumeAiClient.buildMessages("张三 13812348888");

        assertEquals(2, messages.size());
        assertEquals("system", messages.get(0).get("role"));
        String system = messages.get(0).get("content");
        assertTrue(system.contains("JSON"));
        assertTrue(system.contains("education"));
        assertTrue(system.contains("experience"));
        assertEquals("user", messages.get(1).get("role"));
        assertTrue(messages.get(1).get("content").contains("张三 13812348888"));
    }

    @Test
    @DisplayName("extractContent：取 choices[0].message.content")
    void extractContentReadsChoices() {
        String body = "{\"choices\":[{\"message\":{\"content\":\"{\\\"name\\\":\\\"张三\\\"}\"}}]}";
        assertEquals("{\"name\":\"张三\"}", DashScopeResumeAiClient.extractContent(body));
    }

    @Test
    @DisplayName("extractContent：空响应/缺 content → AI 解析失败")
    void extractContentRejectsBadBody() {
        BizException empty = assertThrows(BizException.class,
                () -> DashScopeResumeAiClient.extractContent("  "));
        assertTrue(empty.getMessage().contains("AI 解析失败"));

        BizException missing = assertThrows(BizException.class,
                () -> DashScopeResumeAiClient.extractContent("{\"choices\":[]}"));
        assertTrue(missing.getMessage().contains("AI 解析失败"));
    }

    @Test
    @DisplayName("parseContent：容忍 markdown ```json 围栏")
    void parseContentStripsFence() {
        String fenced = "```json\n{\"name\":\"张三\",\"education\":[{\"school\":\"X大\"}]}\n```";
        var node = DashScopeResumeAiClient.parseContent(fenced);
        assertEquals("张三", node.get("name").asText());
        assertEquals(1, node.get("education").size());
    }

    @Test
    @DisplayName("parseContent：非法 JSON / 非对象 → AI 解析失败（不静默降级）")
    void parseContentRejectsInvalid() {
        BizException badJson = assertThrows(BizException.class,
                () -> DashScopeResumeAiClient.parseContent("这不是 JSON"));
        assertTrue(badJson.getMessage().contains("AI 解析失败"));

        BizException notObject = assertThrows(BizException.class,
                () -> DashScopeResumeAiClient.parseContent("[1,2,3]"));
        assertTrue(notObject.getMessage().contains("AI 解析失败"));
    }

    @Test
    @DisplayName("extract：API key 未配置 → 报错并注明原因，不发起调用")
    void extractFailsFastWithoutApiKey() {
        RecruitProperties props = new RecruitProperties();
        props.getAi().getDashscope().setApiKey(" ");
        DashScopeResumeAiClient client = new DashScopeResumeAiClient(props);

        BizException e = assertThrows(BizException.class, () -> client.extract("text"));
        assertTrue(e.getMessage().contains("DASHSCOPE_API_KEY 未配置"));
    }
}
