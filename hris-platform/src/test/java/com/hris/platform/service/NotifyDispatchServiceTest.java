package com.hris.platform.service;

import com.hris.common.exception.BizException;
import com.hris.common.exception.ErrorCode;
import com.hris.platform.dto.NotificationDtos.TestSendRequest;
import com.hris.platform.dto.NotificationDtos.TestSendResponse;
import com.hris.platform.entity.SysNotifyRecord;
import com.hris.platform.entity.SysNotifyTemplate;
import com.hris.platform.mapper.SysNotifyRecordMapper;
import com.hris.platform.mapper.SysNotifyTemplateMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * NotifyDispatchService 单元测试（PLT-F-003 深化：webhook 机器人分发）。
 *
 * <p>核心守卫：payload 按渠道构造、SSRF 内网拒绝、失败不抛出（摘要返回）、
 * 状态回写 sys_notify_record（SENT/FAILED）。HttpClient 经 WebhookPoster 接口 mock。</p>
 */
@ExtendWith(MockitoExtension.class)
class NotifyDispatchServiceTest {

    @Mock
    private SysNotifyTemplateMapper templateMapper;
    @Mock
    private SysNotifyRecordMapper recordMapper;
    @Mock
    private WebhookPoster poster;

    private NotifyDispatchService service;

    @BeforeEach
    void setUp() {
        service = new NotifyDispatchService(templateMapper, recordMapper, poster);
    }

    private SysNotifyTemplate template(String channel, String webhookUrl) {
        SysNotifyTemplate t = new SysNotifyTemplate();
        t.setId(9L);
        t.setCode("onboard_welcome");
        t.setChannel(channel);
        t.setSubject("欢迎 {name}");
        t.setContent("入职通知：{name} 于 {date} 报到");
        t.setWebhookUrl(webhookUrl);
        return t;
    }

    @Nested
    @DisplayName("payload 构造与投递")
    class Dispatch {

        @Test
        @DisplayName("钉钉渠道：msgtype=text 且 text.content 携带渲染后文本")
        void dingTalkPayload() {
            when(templateMapper.selectById(9L)).thenReturn(template("DINGTALK", "https://oapi.example.com/robot"));
            when(poster.postJson(anyString(), anyString()))
                    .thenReturn(WebhookPoster.PostResult.ok(200, "{}"));

            TestSendResponse resp = service.testSend(9L,
                    new TestSendRequest("https://oapi.example.com/robot"));

            assertEquals("SENT", resp.status());
            assertEquals(200, resp.httpStatus());
            ArgumentCaptor<String> json = ArgumentCaptor.forClass(String.class);
            verify(poster).postJson(eq("https://oapi.example.com/robot"), json.capture());
            assertTrue(json.getValue().contains("\"msgtype\""),
                    "钉钉 payload 应含 msgtype，实际=" + json.getValue());
            assertTrue(json.getValue().contains("入职通知：{name} 于 {date} 报到"),
                    "测试发送无参数，占位符应原样保留，实际=" + json.getValue());
        }

        @Test
        @DisplayName("企微渠道：msgtype=text 且 content 为渲染文本")
        void weComPayload() {
            when(templateMapper.selectById(9L)).thenReturn(template("WECOM", "https://qyapi.example.com/hook"));
            when(poster.postJson(anyString(), anyString()))
                    .thenReturn(WebhookPoster.PostResult.ok(200, "ok"));

            service.testSend(9L, null);

            ArgumentCaptor<String> json = ArgumentCaptor.forClass(String.class);
            verify(poster).postJson(eq("https://qyapi.example.com/hook"), json.capture());
            assertTrue(json.getValue().contains("欢迎 {name}"), "实际=" + json.getValue());
        }

        @Test
        @DisplayName("请求体 webhookUrl 覆盖模板配置地址")
        void requestUrlOverridesTemplate() {
            when(templateMapper.selectById(9L)).thenReturn(template("WECOM", "https://template.example.com/hook"));
            when(poster.postJson(anyString(), anyString()))
                    .thenReturn(WebhookPoster.PostResult.ok(200, "ok"));

            service.testSend(9L, new TestSendRequest("https://override.example.com/hook"));

            verify(poster).postJson(eq("https://override.example.com/hook"), anyString());
        }
    }

    @Nested
    @DisplayName("SSRF 防线")
    class Ssrf {

        @Test
        @DisplayName("localhost / 127.* / 10.* / 192.168.* / 172.16-31.* 一律拒绝，不外呼")
        void rejectsPrivateTargets() {
            when(templateMapper.selectById(9L)).thenReturn(template("WECOM", null));
            String[] bad = {
                    "http://localhost:8080/hook",
                    "http://127.0.0.1:9000/hook",
                    "http://10.1.2.3/hook",
                    "http://192.168.0.10/hook",
                    "http://172.16.0.1/hook",
                    "http://172.31.255.255/hook",
                    "ftp://example.com/hook"};
            for (String url : bad) {
                TestSendResponse resp = service.testSend(9L, new TestSendRequest(url));
                assertEquals("FAILED", resp.status(), url + " 应被拒绝");
                assertTrue(resp.failedReason().contains("内网") || resp.failedReason().contains("http/https"),
                        url + " 拒绝原因不符: " + resp.failedReason());
            }
            // 172.32.0.1 不在私有段 → 放行外呼
            when(poster.postJson(anyString(), anyString()))
                    .thenReturn(WebhookPoster.PostResult.ok(200, "ok"));
            TestSendResponse ok = service.testSend(9L, new TestSendRequest("http://172.32.0.1/hook"));
            assertEquals("SENT", ok.status());
            verify(poster).postJson(eq("http://172.32.0.1/hook"), anyString());
        }

        @Test
        @DisplayName("SSRF 拒绝路径不调用 poster")
        void ssrfRejectDoesNotPost() {
            when(templateMapper.selectById(9L)).thenReturn(template("WECOM", "http://127.0.0.1/hook"));

            TestSendResponse resp = service.testSend(9L, null);

            assertEquals("FAILED", resp.status());
            verify(poster, never()).postJson(anyString(), anyString());
        }
    }

    @Nested
    @DisplayName("失败语义与记录回写")
    class FailureAndRecord {

        @Test
        @DisplayName("webhook 非 2xx → FAILED 摘要返回（不抛异常），记录置 FAILED")
        void non2xxReturnsFailedSummary() {
            when(templateMapper.selectById(9L)).thenReturn(template("WECOM", "https://qyapi.example.com/hook"));
            when(poster.postJson(anyString(), anyString()))
                    .thenReturn(WebhookPoster.PostResult.fail(502, "bad gateway", "webhook 返回非 2xx: HTTP 502"));

            TestSendResponse resp = service.testSend(9L, null);

            assertEquals("FAILED", resp.status());
            assertEquals(502, resp.httpStatus());
            assertEquals("webhook 返回非 2xx: HTTP 502", resp.failedReason());
            ArgumentCaptor<SysNotifyRecord> rec = ArgumentCaptor.forClass(SysNotifyRecord.class);
            verify(recordMapper).insert(rec.capture());
            assertEquals("FAILED", rec.getValue().getSendStatus());
            assertTrue(rec.getValue().getSentAt() == null);
        }

        @Test
        @DisplayName("成功 → 记录置 SENT 且带 sentAt")
        void successWritesSentRecord() {
            when(templateMapper.selectById(9L)).thenReturn(template("DINGTALK", "https://oapi.example.com/robot"));
            when(poster.postJson(anyString(), anyString()))
                    .thenReturn(WebhookPoster.PostResult.ok(200, "{}"));

            service.testSend(9L, null);

            ArgumentCaptor<SysNotifyRecord> rec = ArgumentCaptor.forClass(SysNotifyRecord.class);
            verify(recordMapper).insert(rec.capture());
            assertEquals("SENT", rec.getValue().getSendStatus());
            assertEquals(9L, rec.getValue().getTemplateId());
            assertFalse(rec.getValue().getSentAt() == null);
        }

        @Test
        @DisplayName("模板与请求均无 webhook 地址 → FAILED 摘要，不外呼")
        void missingUrlFails() {
            when(templateMapper.selectById(9L)).thenReturn(template("WECOM", null));

            TestSendResponse resp = service.testSend(9L, null);

            assertEquals("FAILED", resp.status());
            verify(poster, never()).postJson(anyString(), anyString());
        }

        @Test
        @DisplayName("模板不存在 → NOT_FOUND 抛出（幂等契约内的调用方错误）")
        void templateNotFound() {
            when(templateMapper.selectById(404L)).thenReturn(null);

            BizException e = assertThrows(BizException.class,
                    () -> service.testSend(404L, null));

            assertEquals(ErrorCode.NOT_FOUND.getCode(), e.getCode());
            verify(poster, never()).postJson(anyString(), anyString());
        }
    }
}
