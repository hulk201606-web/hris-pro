package com.hris.recruit.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hris.common.exception.BizException;
import com.hris.common.exception.ErrorCode;
import com.hris.recruit.config.RecruitProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * DashScope 简历 AI 解析客户端（批 6 #9）：OpenAI 兼容 chat completions，结构化抽取简历字段。
 *
 * <p>失败语义：API key 未配置 / HTTP 错误 / 超时 / 响应或 JSON 解析失败一律抛
 * BizException(RCS_ERROR, "AI 解析失败：原因")，由调用方保持旧解析结果不变（不静默降级）。</p>
 */
@Component
public class DashScopeResumeAiClient {

    private static final Logger log = LoggerFactory.getLogger(DashScopeResumeAiClient.class);

    private static final ObjectMapper JSON = new ObjectMapper();

    /** 结构化抽取系统提示词：强制 JSON 输出，字段白名单。 */
    static final String SYSTEM_PROMPT = """
            你是简历解析器。从用户提供的简历纯文本中抽取结构化字段，只输出一个 JSON 对象，\
            不要输出任何解释或 markdown 代码块。字段定义：
            name 姓名(字符串,抽不到为 null)、mobile 手机号(字符串,抽不到为 null)、\
            email 邮箱(字符串,抽不到为 null)、
            education 教育经历数组(每项含 school/major/degree/period,抽不到为空数组)、\
            experience 工作经历数组(每项含 company/title/period/summary,抽不到为空数组)。""";

    private final RestClient restClient;
    private final String apiKey;
    private final String chatModel;

    public DashScopeResumeAiClient(RecruitProperties properties) {
        RecruitProperties.Dashscope cfg = properties.getAi().getDashscope();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory();
        factory.setReadTimeout(Duration.ofSeconds(cfg.getTimeoutSeconds()));
        this.restClient = RestClient.builder()
                .baseUrl(cfg.getBaseUrl())
                .requestFactory(factory)
                .build();
        this.apiKey = cfg.getApiKey();
        this.chatModel = cfg.getChatModel();
    }

    /** AI 抽取结果：education/experience 为紧凑 JSON 数组字符串（LLM 原始结构）。 */
    public record AiExtraction(String name, String mobile, String email,
                               String educationJson, String experienceJson) {
    }

    /** 调用 LLM 抽取简历字段；任何失败抛 BizException（AI 解析失败：原因）。 */
    public AiExtraction extract(String text) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new BizException(ErrorCode.RCS_ERROR, "AI 解析失败：DASHSCOPE_API_KEY 未配置");
        }
        String content;
        try {
            Map<String, Object> body = Map.of(
                    "model", chatModel,
                    "messages", buildMessages(text),
                    "temperature", 0.1,
                    "response_format", Map.of("type", "json_object"));
            String resp = restClient.post()
                    .uri("/chat/completions")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(String.class);
            content = extractContent(resp);
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            log.warn("DashScope 调用失败: {}", e.getMessage());
            throw new BizException(ErrorCode.RCS_ERROR, "AI 解析失败：LLM 调用异常（" + e.getMessage() + "）");
        }
        return toExtraction(parseContent(content));
    }

    /** 组装 messages（OpenAI 兼容格式）。包级可见供单测。 */
    static List<Map<String, String>> buildMessages(String text) {
        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content", SYSTEM_PROMPT));
        messages.add(Map.of("role", "user", "content",
                "请解析以下简历文本并输出 JSON：\n" + text));
        return messages;
    }

    /** 从 chat completions 响应体取 choices[0].message.content。包级可见供单测。 */
    static String extractContent(String respBody) {
        if (respBody == null || respBody.isBlank()) {
            throw new BizException(ErrorCode.RCS_ERROR, "AI 解析失败：LLM 响应为空");
        }
        try {
            JsonNode content = JSON.readTree(respBody)
                    .path("choices").path(0).path("message").path("content");
            if (content.isMissingNode() || content.isNull() || content.asText().isBlank()) {
                throw new BizException(ErrorCode.RCS_ERROR, "AI 解析失败：LLM 响应缺少 content");
            }
            return content.asText();
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw new BizException(ErrorCode.RCS_ERROR, "AI 解析失败：LLM 响应格式异常");
        }
    }

    /** 解析 LLM 输出 content（容忍 markdown 围栏）为 JSON 对象。包级可见供单测。 */
    static JsonNode parseContent(String content) {
        String trimmed = content == null ? "" : content.strip();
        if (trimmed.startsWith("```")) {
            trimmed = trimmed.replaceFirst("^```[a-zA-Z]*\\s*", "");
            int fenceEnd = trimmed.lastIndexOf("```");
            if (fenceEnd >= 0) {
                trimmed = trimmed.substring(0, fenceEnd);
            }
            trimmed = trimmed.strip();
        }
        try {
            JsonNode node = JSON.readTree(trimmed);
            if (!node.isObject()) {
                throw new IllegalArgumentException("非 JSON 对象");
            }
            return node;
        } catch (Exception e) {
            throw new BizException(ErrorCode.RCS_ERROR, "AI 解析失败：LLM 输出不是合法 JSON");
        }
    }

    /** JSON 对象 → AiExtraction；education/experience 序列化为紧凑 JSON 数组字符串。 */
    private AiExtraction toExtraction(JsonNode node) {
        return new AiExtraction(
                textOrNull(node.get("name")),
                textOrNull(node.get("mobile")),
                textOrNull(node.get("email")),
                arrayToJson(node.get("education")),
                arrayToJson(node.get("experience")));
    }

    private static String textOrNull(JsonNode n) {
        return n == null || n.isNull() ? null : n.asText();
    }

    private String arrayToJson(JsonNode n) {
        if (n == null || n.isNull() || !n.isArray() || n.isEmpty()) {
            return null;
        }
        try {
            return JSON.writeValueAsString(n);
        } catch (Exception e) {
            throw new BizException(ErrorCode.RCS_ERROR, "AI 解析失败：抽取结果序列化异常");
        }
    }
}
