package com.hris.rag.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 问答域 DTO（KBD-F-008/009/012）。
 */
public final class QaDtos {

    private QaDtos() {
    }

    /** 创建会话请求（POST /kb/chat/sessions） */
    public record SessionCreateRequest(Long spaceId, @Size(max = 128) String title) {
    }

    /** 提问请求（POST /kb/chat/sessions/{id}/messages） */
    public record AskRequest(@NotBlank String question) {
    }

    /** 反馈请求（POST /kb/chat/messages/{messageId}/feedback） */
    public record FeedbackRequest(boolean helpful, @Size(max = 512) String comment) {
    }

    /** 会话响应 */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record SessionResponse(Long id, Long userId, String title, LocalDateTime createdAt) {
    }

    /** 引用项（citations 数组元素） */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Citation(Long docId, String title, Long chunkId, Double score) {
    }

    /** 消息响应 */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record MessageResponse(Long id, Long sessionId, String question, String answer,
                                  List<Citation> citations, String answerStatus,
                                  Integer latencyMs, String model, LocalDateTime createdAt) {
    }
}
