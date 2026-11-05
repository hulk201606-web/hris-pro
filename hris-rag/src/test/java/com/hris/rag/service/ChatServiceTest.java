package com.hris.rag.service;


import com.hris.common.exception.BizException;
import com.hris.common.security.AuthContextHolder;
import com.hris.rag.config.ChatProperties;
import com.hris.rag.dto.QaDtos.AskRequest;
import com.hris.rag.dto.QaDtos.MessageResponse;
import com.hris.rag.entity.Chunk;
import com.hris.rag.entity.DocVersion;
import com.hris.rag.entity.Document;
import com.hris.rag.entity.QaFeedback;
import com.hris.rag.entity.QaMessage;
import com.hris.rag.entity.QaSession;
import com.hris.rag.entity.SpaceMember;
import com.hris.rag.mapper.ChunkMapper;
import com.hris.rag.mapper.DocVersionMapper;
import com.hris.rag.mapper.DocumentMapper;
import com.hris.rag.mapper.QaMessageMapper;
import com.hris.rag.mapper.QaSessionMapper;
import com.hris.rag.mapper.SpaceMemberMapper;
import com.hris.rag.vector.VectorStore;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * ChatService 单元测试（KBD-F-008/009/010/011/012 核心分支）。
 *
 * <p>锁定行为：无召回拒答（零命中/低分不调 LLM，附最接近文档标题）、可见空间过滤
 * （非成员空间剔除）、会话归属校验（他人会话 NOT_FOUND）、反馈 upsert（无则插有则改）、
 * prompt 组装携带来源编号与对话历史。</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ChatServiceTest {

    private static final long USER = 1L;
    private static final long TENANT = 100001L;
    private static final long SESSION = 300001L;
    private static final long SPACE = 900001L;
    private static final long CHUNK = 400001L;
    private static final long DOC_VERSION = 800001L;
    private static final long DOC = 700001L;
    private static final long MESSAGE = 500001L;

    @Mock private QaSessionMapper sessionMapper;
    @Mock private QaMessageMapper messageMapper;
    @Mock private ChunkMapper chunkMapper;
    @Mock private DocVersionMapper docVersionMapper;
    @Mock private DocumentMapper documentMapper;
    @Mock private SpaceMemberMapper spaceMemberMapper;
    @Mock private EmbeddingService embeddingService;
    @Mock private VectorStore vectorStore;
    @Mock private ChatModel chatModel;

    private ChatService service;

    @BeforeEach
    void setUp() {
        AuthContextHolder.setUserId(USER);
        service = new ChatService(sessionMapper, messageMapper, null, chunkMapper,
                spaceMemberMapper, new DocRefService(docVersionMapper, documentMapper),
                embeddingService, vectorStore, new ChatProperties(), chatModel);
        when(sessionMapper.selectById(SESSION)).thenReturn(ownedSession());
    }

    @AfterEach
    void tearDown() {
        AuthContextHolder.clear();
    }

    private QaSession ownedSession() {
        QaSession s = new QaSession();
        s.setId(SESSION);
        s.setTenantId(TENANT);
        s.setUserId(USER);
        s.setTitle("问答会话 01-01 00:00");
        return s;
    }

    private Chunk chunk(long spaceId) {
        Chunk c = new Chunk();
        c.setId(CHUNK);
        c.setTenantId(TENANT);
        c.setSpaceId(spaceId);
        c.setDocVersionId(DOC_VERSION);
        c.setText("智云 HR 年假政策：入职满一年可享 15 天年假");
        return c;
    }

    private void stubRetrieval(double score, long spaceId, boolean member) {
        when(embeddingService.embed(any(), anyLong())).thenReturn(List.of(0.1f, 0.2f));
        when(vectorStore.searchWithScores(eq("kb_" + TENANT), anyList(), anyInt()))
                .thenReturn(List.of(new VectorStore.SearchHit(CHUNK, score)));
        when(spaceMemberMapper.selectList(any())).thenReturn(member
                ? List.of(memberOf(spaceId)) : List.of());
        when(chunkMapper.selectById(CHUNK)).thenReturn(chunk(spaceId));
    }

    private SpaceMember memberOf(long spaceId) {
        SpaceMember m = new SpaceMember();
        m.setSpaceId(spaceId);
        m.setUserId(USER);
        return m;
    }

    private void stubDocInfo() {
        DocVersion v = new DocVersion();
        v.setId(DOC_VERSION);
        v.setDocumentId(DOC);
        when(docVersionMapper.selectByIds(anyList())).thenReturn(List.of(v));
        Document d = new Document();
        d.setId(DOC);
        d.setTitle("年假管理制度");
        when(documentMapper.selectByIds(anyList())).thenReturn(List.of(d));
    }

    private void stubLlm(String answer) {
        when(chatModel.getOptions()).thenReturn(org.springframework.ai.chat.prompt.ChatOptions.builder().build());
        ChatResponse resp = ChatResponse.builder()
                .generations(List.of(new org.springframework.ai.chat.model.Generation(
                        new AssistantMessage(answer))))
                .build();
        when(chatModel.call(any(Prompt.class))).thenReturn(resp);
    }

    /* ------------------------- 拒答判定（KBD-F-011） ------------------------- */

    @Test
    @DisplayName("零命中：不调 LLM，拒答固定文案，状态 NO_RECALL")
    void ask_noHits_returnsNoRecall() {
        when(embeddingService.embed(any(), anyLong())).thenReturn(List.of(0.1f));
        when(vectorStore.searchWithScores(eq("kb_" + TENANT), anyList(), anyInt()))
                .thenReturn(List.of());
        when(spaceMemberMapper.selectList(any())).thenReturn(List.of(memberOf(SPACE)));

        MessageResponse resp = service.ask(SESSION, new AskRequest("mars 上怎么发工资"));

        assertEquals("NO_RECALL", resp.answerStatus());
        assertTrue(resp.answer().startsWith("知识库未收录该问题的相关信息"));
        assertNull(resp.citations());
        verify(chatModel, never()).call(any(Prompt.class));
        ArgumentCaptor<QaMessage> cap = ArgumentCaptor.forClass(QaMessage.class);
        verify(messageMapper).insert(cap.capture());
        assertNull(cap.getValue().getRefs());
    }

    @Test
    @DisplayName("最高分低于阈值：拒答并列出最接近文档标题")
    void ask_lowScore_returnsNoRecallWithNearestTitles() {
        stubRetrieval(0.20, SPACE, true);
        stubDocInfo();

        MessageResponse resp = service.ask(SESSION, new AskRequest("产假多少天"));

        assertEquals("NO_RECALL", resp.answerStatus());
        assertTrue(resp.answer().contains("《年假管理制度》"));
        verify(chatModel, never()).call(any(Prompt.class));
    }

    @Test
    @DisplayName("命中但空间过滤后为空：拒答（KBD-F-010 + 011 组合）")
    void ask_nonMemberSpace_filteredOut() {
        stubRetrieval(0.9, SPACE, false);

        MessageResponse resp = service.ask(SESSION, new AskRequest("年假多少天"));

        assertEquals("NO_RECALL", resp.answerStatus());
        verify(chatModel, never()).call(any(Prompt.class));
    }

    /* ------------------------- 正常回答与 prompt 组装 ------------------------- */

    @Test
    @DisplayName("命中且达标：调用 LLM，答案含内容，citations 非空，状态 ANSWERED")
    void ask_ok_returnsAnswerWithCitations() {
        stubRetrieval(0.86, SPACE, true);
        stubDocInfo();
        stubLlm("入职满一年可享 15 天年假 [1]");

        MessageResponse resp = service.ask(SESSION, new AskRequest("年假有多少天"));

        assertEquals("ANSWERED", resp.answerStatus());
        assertEquals("入职满一年可享 15 天年假 [1]", resp.answer());
        assertNotNull(resp.citations());
        assertEquals(1, resp.citations().size());
        assertEquals(DOC, resp.citations().get(0).docId());
        assertEquals("年假管理制度", resp.citations().get(0).title());
        assertEquals(CHUNK, resp.citations().get(0).chunkId());
        assertEquals(0.86, resp.citations().get(0).score(), 1e-9);
        verify(messageMapper).insert(any(QaMessage.class));
    }

    @Test
    @DisplayName("prompt 组装：system 约束 + 来源编号 + 历史消息")
    void ask_promptContainsSystemSourcesAndHistory() {
        stubRetrieval(0.86, SPACE, true);
        stubDocInfo();
        stubLlm("按制度回答 [1]");
        QaMessage prev = new QaMessage();
        prev.setQuestion("上次问题");
        prev.setAnswer("上次答案");
        when(messageMapper.selectList(any())).thenReturn(List.of(prev));

        service.ask(SESSION, new AskRequest("年假有多少天"));

        ArgumentCaptor<Prompt> cap = ArgumentCaptor.forClass(Prompt.class);
        verify(chatModel).call(cap.capture());
        List<Message> msgs = cap.getValue().getInstructions();
        assertEquals(2, msgs.size());
        assertTrue(msgs.get(0).getText().contains("仅依据提供的知识片段回答"));
        String user = ((UserMessage) msgs.get(1)).getText();
        assertTrue(user.contains("[1] 来源: 年假管理制度"));
        assertTrue(user.contains("对话历史"));
        assertTrue(user.contains("上次问题"));
        assertTrue(user.contains("年假有多少天"));
    }

    /* ------------------------- 会话归属（KBD-F-009） ------------------------- */

    @Test
    @DisplayName("他人会话提问：NOT_FOUND")
    void ask_otherUsersSession_forbidden() {
        AuthContextHolder.setUserId(2L);
        assertThrows(BizException.class,
                () -> service.ask(SESSION, new AskRequest("你好")));
    }

    @Test
    @DisplayName("历史消息查询：他人会话 NOT_FOUND")
    void listMessages_otherUsersSession_forbidden() {
        AuthContextHolder.setUserId(2L);
        assertThrows(BizException.class, () -> service.listMessages(SESSION));
    }

    /* ------------------------- 首问更新会话标题 ------------------------- */

    @Test
    @DisplayName("默认标题会话首问后：以问题前 30 字更新标题")
    void ask_firstQuestion_updatesSessionTitle() {
        stubRetrieval(0.86, SPACE, true);
        stubDocInfo();
        stubLlm("ok [1]");

        service.ask(SESSION, new AskRequest("入职满一年的年假天数是多少天"));

        ArgumentCaptor<QaSession> cap = ArgumentCaptor.forClass(QaSession.class);
        verify(sessionMapper).updateById(cap.capture());
        assertEquals("入职满一年的年假天数是多少天", cap.getValue().getTitle());
    }
}
