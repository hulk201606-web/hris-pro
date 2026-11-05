package com.hris.rag.controller;

import com.hris.common.api.R;
import com.hris.common.dto.PageResult;
import com.hris.common.security.RequirePerm;
import com.hris.rag.dto.QaDtos.AskRequest;
import com.hris.rag.dto.QaDtos.FeedbackRequest;
import com.hris.rag.dto.QaDtos.MessageResponse;
import com.hris.rag.dto.QaDtos.SessionCreateRequest;
import com.hris.rag.dto.QaDtos.SessionResponse;
import com.hris.rag.service.ChatService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 智能问答端点（KBD-F-008/009/012）。
 * 权限码复用 kb 域：kb:chat:read / kb:chat:write（新增，需 seed RBAC）。
 */
@RestController
@RequestMapping("/api/v1/kb/chat")
public class ChatController {

    private final ChatService service;

    public ChatController(ChatService service) {
        this.service = service;
    }

    /** 创建问答会话（KBD-F-009）。 */
    @RequirePerm("kb:chat:write")
    @PostMapping("/sessions")
    public R<SessionResponse> createSession(@Valid @RequestBody SessionCreateRequest req) {
        return R.ok(service.createSession(req));
    }

    /** 我的会话列表（KBD-F-009）。 */
    @RequirePerm("kb:chat:read")
    @GetMapping("/sessions")
    public R<List<SessionResponse>> listSessions() {
        return R.ok(service.listSessions());
    }

    /** 会话历史消息（KBD-F-009）。 */
    @RequirePerm("kb:chat:read")
    @GetMapping("/sessions/{id}/messages")
    public R<List<MessageResponse>> listMessages(@PathVariable Long id) {
        return R.ok(service.listMessages(id));
    }

    /** 提问并回答（KBD-F-008/010/011）。 */
    @RequirePerm("kb:chat:write")
    @PostMapping("/sessions/{id}/messages")
    public R<MessageResponse> ask(@PathVariable Long id, @Valid @RequestBody AskRequest req) {
        return R.ok(service.ask(id, req));
    }

    /** 消息反馈 upsert（KBD-F-012）。 */
    @RequirePerm("kb:chat:write")
    @PostMapping("/messages/{messageId}/feedback")
    public R<Void> feedback(@PathVariable Long messageId, @Valid @RequestBody FeedbackRequest req) {
        service.feedback(messageId, req);
        return R.ok();
    }
}
