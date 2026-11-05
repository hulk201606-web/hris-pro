package com.hris.rag.service;

import com.hris.common.exception.BizException;
import com.hris.common.security.AuthContextHolder;
import com.hris.rag.dto.QaDtos.FeedbackRequest;
import com.hris.rag.entity.QaFeedback;
import com.hris.rag.entity.QaMessage;
import com.hris.rag.mapper.QaFeedbackMapper;
import com.hris.rag.mapper.QaMessageMapper;
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

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 反馈 upsert 单测（KBD-F-012）：本人消息才可反馈，无则插、有则改。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ChatFeedbackTest {

    private static final long USER = 1L;
    private static final long MESSAGE = 500001L;

    @Mock private QaMessageMapper messageMapper;
    @Mock private QaFeedbackMapper feedbackMapper;
    @Mock private org.springframework.ai.chat.model.ChatModel chatModel;

    private ChatService service;

    @BeforeEach
    void setUp() {
        AuthContextHolder.setUserId(USER);
        // 反馈链路只触碰 message/feedback 两张表，其余依赖传 null 不参与调用
        service = new ChatService(null, messageMapper, feedbackMapper, null, null,
                new DocRefService(null, null), null, null,
                new com.hris.rag.config.ChatProperties(), chatModel);
    }

    @AfterEach
    void tearDown() {
        AuthContextHolder.clear();
    }

    private QaMessage myMessage() {
        QaMessage msg = new QaMessage();
        msg.setId(MESSAGE);
        msg.setCreatedBy(USER);
        return msg;
    }

    @Test
    @DisplayName("首次反馈：插入 LIKE/DISLIKE 与纠错文本")
    void feedback_firstTime_inserts() {
        when(messageMapper.selectById(MESSAGE)).thenReturn(myMessage());
        when(feedbackMapper.selectList(any())).thenReturn(List.of());

        service.feedback(MESSAGE, new FeedbackRequest(false, "应该写 10 天"));

        ArgumentCaptor<QaFeedback> cap = ArgumentCaptor.forClass(QaFeedback.class);
        verify(feedbackMapper).insert(cap.capture());
        assertEquals("DISLIKE", cap.getValue().getType());
        assertEquals("应该写 10 天", cap.getValue().getCorrectionText());
        assertEquals(MESSAGE, cap.getValue().getMessageId());
    }

    @Test
    @DisplayName("重复反馈：更新既有记录而非新增")
    void feedback_repeat_updates() {
        when(messageMapper.selectById(MESSAGE)).thenReturn(myMessage());
        QaFeedback existing = new QaFeedback();
        existing.setId(900L);
        existing.setMessageId(MESSAGE);
        existing.setType("LIKE");
        when(feedbackMapper.selectList(any())).thenReturn(List.of(existing));

        service.feedback(MESSAGE, new FeedbackRequest(false, null));

        ArgumentCaptor<QaFeedback> cap = ArgumentCaptor.forClass(QaFeedback.class);
        verify(feedbackMapper).updateById(cap.capture());
        verify(feedbackMapper, never()).insert(any(QaFeedback.class));
        assertEquals("DISLIKE", cap.getValue().getType());
    }

    @Test
    @DisplayName("非本人消息反馈：NOT_FOUND")
    void feedback_otherUsersMessage_forbidden() {
        QaMessage msg = new QaMessage();
        msg.setId(MESSAGE);
        msg.setCreatedBy(999L);
        when(messageMapper.selectById(MESSAGE)).thenReturn(msg);
        assertThrows(BizException.class,
                () -> service.feedback(MESSAGE, new FeedbackRequest(true, null)));
    }
}
