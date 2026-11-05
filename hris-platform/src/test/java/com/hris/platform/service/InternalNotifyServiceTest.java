package com.hris.platform.service;

import com.hris.platform.dto.InternalNotifyDtos.NotifyRequest;
import com.hris.platform.dto.InternalNotifyDtos.NotifyResponse;
import com.hris.platform.entity.SysNotifyTemplate;
import com.hris.platform.mapper.SysNotifyTemplateMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 平台内部通知（RCS-F-018 Offer 发送）：三渠道路由 / 地址解析 / 失败不抛 500。
 */
@ExtendWith(MockitoExtension.class)
class InternalNotifyServiceTest {

    @Mock
    private NotifyDispatchService dispatchService;
    @Mock
    private TenantConfigService configService;
    @Mock
    private SysNotifyTemplateMapper templateMapper;

    private InternalNotifyService service;

    @BeforeEach
    void setUp() {
        service = new InternalNotifyService(dispatchService, configService, templateMapper);
    }

    private NotifyRequest req(String channel) {
        return new NotifyRequest(channel, "hr@x.com", "Offer 通知", "请查收 Offer", "OFFER", "OFFER_1");
    }

    @Test
    @DisplayName("EMAIL：读租户邮件网关配置并投递，返回 SENT")
    void routesEmail() {
        when(configService.findValue("notify.email.webhook")).thenReturn("https://mail.example.com/hook");
        when(dispatchService.sendDirect(eq("EMAIL"), anyString(), anyString(), anyString(),
                eq("https://mail.example.com/hook")))
                .thenReturn(new NotifyDispatchService.DirectSendResult("SENT", 200, "ok", null));

        NotifyResponse resp = service.notify(req("email"));

        assertTrue(resp.success());
        assertEquals("SENT", resp.status());
        assertEquals("EMAIL", resp.channel());
    }

    @Test
    @DisplayName("WECOM/DINGTALK：各自配置键独立解析")
    void routesWeComAndDingTalk() {
        when(configService.findValue("notify.wecom.webhook")).thenReturn("https://qyapi.example.com/hook");
        when(configService.findValue("notify.dingtalk.webhook")).thenReturn("https://oapi.example.com/robot");
        when(dispatchService.sendDirect(eq("WECOM"), any(), any(), any(), anyString()))
                .thenReturn(new NotifyDispatchService.DirectSendResult("SENT", 200, "ok", null));
        when(dispatchService.sendDirect(eq("DINGTALK"), any(), any(), any(), anyString()))
                .thenReturn(new NotifyDispatchService.DirectSendResult("SENT", 200, "ok", null));

        assertTrue(service.notify(req("WECOM")).success());
        assertTrue(service.notify(req("DINGTALK")).success());
        verify(dispatchService).sendDirect(eq("WECOM"), any(), any(), any(),
                eq("https://qyapi.example.com/hook"));
        verify(dispatchService).sendDirect(eq("DINGTALK"), any(), any(), any(),
                eq("https://oapi.example.com/robot"));
    }

    @Test
    @DisplayName("租户未配置 → 回退同渠道模板 webhook")
    void fallsBackToTemplateWebhook() {
        when(configService.findValue("notify.wecom.webhook")).thenReturn(null);
        SysNotifyTemplate t = new SysNotifyTemplate();
        t.setChannel("WECOM");
        t.setWebhookUrl("https://template.example.com/hook");
        when(templateMapper.selectList(any())).thenReturn(List.of(t));
        when(dispatchService.sendDirect(eq("WECOM"), any(), any(), any(),
                eq("https://template.example.com/hook")))
                .thenReturn(new NotifyDispatchService.DirectSendResult("SENT", 200, "ok", null));

        assertTrue(service.notify(req("WECOM")).success());
    }

    @Test
    @DisplayName("发送失败 → success=false + error，HTTP 语义仍成功（不抛异常）")
    void failureReturnsFlag() {
        when(configService.findValue("notify.email.webhook")).thenReturn("https://mail.example.com/hook");
        when(dispatchService.sendDirect(eq("EMAIL"), any(), any(), any(), anyString()))
                .thenReturn(new NotifyDispatchService.DirectSendResult("FAILED", 502, "bad",
                        "webhook 返回非 2xx: HTTP 502"));

        NotifyResponse resp = service.notify(req("EMAIL"));

        assertFalse(resp.success());
        assertEquals("FAILED", resp.status());
        assertTrue(resp.error().contains("502"));
    }

    @Test
    @DisplayName("不支持渠道 → 直接失败，不外呼")
    void rejectsUnsupportedChannel() {
        NotifyResponse resp = service.notify(req("SMS"));

        assertFalse(resp.success());
        assertTrue(resp.error().contains("不支持的渠道"));
        verify(dispatchService, never()).sendDirect(any(), any(), any(), any(), any());
    }
}
