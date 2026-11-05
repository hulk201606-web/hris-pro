package com.hris.rag.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hris.common.exception.BizException;
import com.hris.common.exception.ErrorCode;
import com.hris.rag.config.ChatProperties;
import com.hris.rag.dto.QaDtos.AskRequest;
import com.hris.rag.dto.QaDtos.Citation;
import com.hris.rag.dto.QaDtos.FeedbackRequest;
import com.hris.rag.dto.QaDtos.MessageResponse;
import com.hris.rag.dto.QaDtos.SessionCreateRequest;
import com.hris.rag.dto.QaDtos.SessionResponse;
import com.hris.rag.entity.Chunk;
import com.hris.rag.entity.QaFeedback;
import com.hris.rag.entity.QaMessage;
import com.hris.rag.entity.QaSession;
import com.hris.rag.entity.SpaceMember;
import com.hris.rag.mapper.ChunkMapper;
import com.hris.rag.mapper.QaFeedbackMapper;
import com.hris.rag.mapper.QaMessageMapper;
import com.hris.rag.mapper.QaSessionMapper;
import com.hris.rag.mapper.SpaceMemberMapper;
import com.hris.rag.vector.VectorStore;
import com.hris.common.security.AuthContextHolder;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 智能问答服务（KBD-F-008/009/010/011/012）。
 *
 * <p>链路：embed(question) → Milvus 检索 kb_{tenantId}（带 COSINE 相似度得分）→
 * 按用户可见空间过滤（KBD-F-010：非空间成员的空间 chunk 剔除）→
 * 阈值判定（KBD-F-011：最高分低于阈值或零命中即拒答）→
 * ChatClient 组装多轮历史 + 知识片段（KBD-F-009/008）→ 落 qa_message（citations JSON）。
 * 反馈 upsert qa_feedback（KBD-F-012）。文档标题/引用装配见 {@link DocRefService}。</p>
 */
@Service
public class ChatService {

    private static final int NEAREST_HINT_COUNT = 3;  // 拒答时附带的最接近文档标题数
    private static final DateTimeFormatter TITLE_FMT = DateTimeFormatter.ofPattern("MM-dd HH:mm");

    private final QaSessionMapper sessionMapper;
    private final QaMessageMapper messageMapper;
    private final QaFeedbackMapper feedbackMapper;
    private final ChunkMapper chunkMapper;
    private final SpaceMemberMapper spaceMemberMapper;
    private final DocRefService docRefService;
    private final EmbeddingService embeddingService;
    private final VectorStore vectorStore;
    private final ChatProperties props;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ChatClient chatClient;
    private final String modelName;

    public ChatService(QaSessionMapper sessionMapper, QaMessageMapper messageMapper,
                       QaFeedbackMapper feedbackMapper, ChunkMapper chunkMapper,
                       SpaceMemberMapper spaceMemberMapper, DocRefService docRefService,
                       EmbeddingService embeddingService, VectorStore vectorStore,
                       ChatProperties props, ChatModel chatModel) {
        this.sessionMapper = sessionMapper;
        this.messageMapper = messageMapper;
        this.feedbackMapper = feedbackMapper;
        this.chunkMapper = chunkMapper;
        this.spaceMemberMapper = spaceMemberMapper;
        this.docRefService = docRefService;
        this.embeddingService = embeddingService;
        this.vectorStore = vectorStore;
        this.props = props;
        this.chatClient = ChatClient.builder(chatModel).build();
        this.modelName = resolveModelName(chatModel);
    }

    /* ----------------------------- 会话（KBD-F-009） ----------------------------- */

    @Transactional(rollbackFor = Exception.class)
    public SessionResponse createSession(SessionCreateRequest req) {
        QaSession e = new QaSession();
        e.setUserId(currentUserId());
        e.setTitle(req.title() == null || req.title().isBlank()
                ? "问答会话 " + LocalDateTime.now().format(TITLE_FMT) : req.title());
        sessionMapper.insert(e);
        return toSessionResponse(e);
    }

    /** 我的会话列表（按创建时间倒序）。 */
    public List<SessionResponse> listSessions() {
        return sessionMapper.selectList(new LambdaQueryWrapper<QaSession>()
                        .eq(QaSession::getUserId, currentUserId())
                        .orderByDesc(QaSession::getCreatedAt))
                .stream().map(this::toSessionResponse).toList();
    }

    /** 会话历史消息（按创建时间正序）。 */
    public List<MessageResponse> listMessages(Long sessionId) {
        requireOwnedSession(sessionId);
        return messageMapper.selectList(new LambdaQueryWrapper<QaMessage>()
                        .eq(QaMessage::getSessionId, sessionId)
                        .orderByAsc(QaMessage::getCreatedAt))
                .stream().map(this::toMessageResponse).toList();
    }

    /* ----------------------------- 提问（KBD-F-008/010/011） ----------------------------- */

    /**
     * 提问并回答。检索结果按可见空间过滤后做阈值判定；低于阈值或零命中拒答（不调用 LLM）。
     */
    @Transactional(rollbackFor = Exception.class)
    public MessageResponse ask(Long sessionId, AskRequest req) {
        QaSession session = requireOwnedSession(sessionId);
        String question = req.question().trim();
        long t0 = System.currentTimeMillis();

        List<Float> vector = embeddingService.embed(question, question.hashCode());
        List<VectorStore.SearchHit> hits = vectorStore.searchWithScores(
                "kb_" + session.getTenantId(), vector, props.getCandidateTopK());
        List<Ranked> ranked = filterByVisibleSpaces(hits);

        boolean noRecall = ranked.isEmpty()
                || ranked.get(0).score() < props.getRecallThreshold();
        if (noRecall) {
            List<String> nearest = nearestTitles(ranked);
            return saveMessage(session.getId(), question,
                    ChatPromptBuilder.buildNoRecallAnswer(nearest), List.of(),
                    "NO_RECALL", (int) (System.currentTimeMillis() - t0), session);
        }

        List<Ranked> top = ranked.subList(0, Math.min(props.getTopK(), ranked.size()));
        List<Chunk> topChunks = top.stream().map(Ranked::chunk).toList();
        DocRefService.DocInfo docInfo = docRefService.load(topChunks);
        String userPrompt = ChatPromptBuilder.buildUserPrompt(
                loadHistory(sessionId), topChunks, docInfo.titles(), question);
        String answer = chatClient.prompt()
                .system(ChatPromptBuilder.SYSTEM_PROMPT)
                .user(userPrompt)
                .call()
                .content();
        List<Citation> citations = buildCitations(ranked, docInfo);
        return saveMessage(session.getId(), question, answer, citations,
                "ANSWERED", (int) (System.currentTimeMillis() - t0), session);
    }

    /** 命中切片按得分降序，仅保留当前用户为空间成员的 chunk（KBD-F-010）。 */
    private List<Ranked> filterByVisibleSpaces(List<VectorStore.SearchHit> hits) {
        List<Long> visibleSpaceIds = spaceMemberMapper.selectList(
                        new LambdaQueryWrapper<SpaceMember>().eq(SpaceMember::getUserId, currentUserId()))
                .stream().map(SpaceMember::getSpaceId).distinct().toList();
        List<Ranked> out = new ArrayList<>();
        for (VectorStore.SearchHit hit : hits) {
            Chunk c = chunkMapper.selectById(hit.chunkId());
            if (c != null && c.getSpaceId() != null && visibleSpaceIds.contains(c.getSpaceId())) {
                out.add(new Ranked(c, hit.score()));
            }
        }
        return out;
    }

    private record Ranked(Chunk chunk, double score) {
    }
    /** 最接近的文档标题（拒绝提示用，仅取过滤后可见项）。 */
    private List<String> nearestTitles(List<Ranked> ranked) {
        List<Chunk> nearest = ranked.stream().limit(NEAREST_HINT_COUNT)
                .map(Ranked::chunk).toList();
        DocRefService.DocInfo info = docRefService.load(nearest);
        return nearest.stream().map(c -> info.titles().get(c.getId()))
                .filter(Objects::nonNull).toList();
    }

    /** 引用列表：全部可见命中（按得分降序），含 docId/title/chunkId/score。 */
    private List<Citation> buildCitations(List<Ranked> ranked, DocRefService.DocInfo docInfo) {
        List<Citation> out = new ArrayList<>();
        for (Ranked r : ranked) {
            out.add(new Citation(docInfo.docIds().get(r.chunk().getId()),
                    docInfo.titles().get(r.chunk().getId()),
                    r.chunk().getId(), r.score()));
        }
        return out;
    }

    /** 最近 N 条历史消息（时间升序，KBD-F-009）。 */
    private List<QaMessage> loadHistory(Long sessionId) {
        List<QaMessage> rows = messageMapper.selectList(new LambdaQueryWrapper<QaMessage>()
                .eq(QaMessage::getSessionId, sessionId)
                .orderByDesc(QaMessage::getCreatedAt)
                .last("LIMIT " + props.getHistoryTurns()));
        java.util.Collections.reverse(rows);
        return rows;
    }

    private MessageResponse saveMessage(Long sessionId, String question, String answer,
                                        List<Citation> citations, String status, int latencyMs,
                                        QaSession session) {
        QaMessage m = new QaMessage();
        m.setSessionId(sessionId);
        m.setRole("ASSISTANT");
        m.setQuestion(question);
        m.setAnswer(answer);
        m.setAnswerStatus(status);
        m.setLatencyMs(latencyMs);
        m.setModel(modelName);
        try {
            m.setRefs(citations.isEmpty() ? null : objectMapper.writeValueAsString(citations));
        } catch (Exception e) {
            throw new BizException(ErrorCode.RAG_ERROR, "引用序列化失败");
        }
        messageMapper.insert(m);
        // 首条消息用问题更新会话标题（默认标题为占位格式）
        if (session.getTitle() != null && session.getTitle().startsWith("问答会话") && !question.isEmpty()) {
            session.setTitle(question.length() > 30 ? question.substring(0, 30) : question);
            sessionMapper.updateById(session);
        }
        return toMessageResponse(m);
    }

    /* ----------------------------- 反馈（KBD-F-012） ----------------------------- */

    /** 本人消息反馈 upsert：helpful=true 落 LIKE，否则 DISLIKE；comment 为纠错文本。 */
    @Transactional(rollbackFor = Exception.class)
    public void feedback(Long messageId, FeedbackRequest req) {
        Long userId = currentUserId();
        QaMessage msg = messageMapper.selectById(messageId);
        if (msg == null || !Objects.equals(msg.getCreatedBy(), userId)) {
            throw new BizException(ErrorCode.NOT_FOUND, "消息不存在");
        }
        QaFeedback existing = feedbackMapper.selectList(new LambdaQueryWrapper<QaFeedback>()
                        .eq(QaFeedback::getMessageId, messageId)
                        .eq(QaFeedback::getCreatedBy, userId)
                        .last("LIMIT 1"))
                .stream().findFirst().orElse(null);
        String type = req.helpful() ? "LIKE" : "DISLIKE";
        if (existing == null) {
            QaFeedback f = new QaFeedback();
            f.setMessageId(messageId);
            f.setType(type);
            f.setCorrectionText(req.comment());
            feedbackMapper.insert(f);
        } else {
            existing.setType(type);
            existing.setCorrectionText(req.comment());
            feedbackMapper.updateById(existing);
        }
    }

    /* ----------------------------- 内部 ----------------------------- */

    private Long currentUserId() {
        Long uid = AuthContextHolder.getUserId();
        if (uid == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "未认证");
        }
        return uid;
    }

    /** 校验会话存在且归属当前用户，否则 NOT_FOUND（不泄露他人会话存在性）。 */
    private QaSession requireOwnedSession(Long sessionId) {
        QaSession s = sessionMapper.selectById(sessionId);
        if (s == null || !Objects.equals(s.getUserId(), currentUserId())) {
            throw new BizException(ErrorCode.NOT_FOUND, "会话不存在");
        }
        return s;
    }

    private SessionResponse toSessionResponse(QaSession e) {
        return new SessionResponse(e.getId(), e.getUserId(), e.getTitle(), e.getCreatedAt());
    }

    private MessageResponse toMessageResponse(QaMessage e) {
        List<Citation> citations = null;
        if (e.getRefs() != null && !e.getRefs().isBlank()) {
            try {
                citations = objectMapper.readValue(e.getRefs(), new TypeReference<List<Citation>>() { });
            } catch (Exception ex) {
                citations = List.of();
            }
        }
        return new MessageResponse(e.getId(), e.getSessionId(), e.getQuestion(), e.getAnswer(),
                citations, e.getAnswerStatus(), e.getLatencyMs(), e.getModel(), e.getCreatedAt());
    }

    private String resolveModelName(ChatModel chatModel) {
        try {
            if (chatModel instanceof OpenAiChatModel om) {
                String m = om.getDefaultOptions().getModel();
                return m == null || m.isBlank() ? "unknown" : m;
            }
        } catch (Exception ignored) { /* 非 OpenAI 实现时降级 */ }
        return "unknown";
    }
}
