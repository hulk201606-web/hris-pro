package com.hris.platform.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hris.platform.dto.InternalNotifyDtos.NotifyRequest;
import com.hris.platform.dto.InternalNotifyDtos.NotifyResponse;
import com.hris.platform.entity.SysNotifyTemplate;
import com.hris.platform.mapper.SysNotifyTemplateMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 平台内部通知（POST /internal/plt/notify，RCS-F-018 Offer 发送）。
 *
 * <p>三渠道路由复用 {@link NotifyDispatchService#sendDirect}：EMAIL 走邮件网关
 * webhook，WECOM/DINGTALK 走机器人 webhook。地址解析顺序：</p>
 * <ol>
 *   <li>租户配置 {@code notify.<channel>.webhook}（运维可改，无需发版）；</li>
 *   <li>同渠道任一配置了 webhook_url 的通知模板（存量模板兜底）。</li>
 * </ol>
 *
 * <p>未配置地址/发送失败均返回 success=false + error，不抛 500——招聘侧要按
 * FAILED 回执，而不是把 Offer 发送流程打成服务异常。</p>
 */
@Service
public class InternalNotifyService {

    private static final Logger log = LoggerFactory.getLogger(InternalNotifyService.class);
    private static final Set<String> SUPPORTED = Set.of("EMAIL", "WECOM", "DINGTALK");

    private final NotifyDispatchService dispatchService;
    private final TenantConfigService configService;
    private final SysNotifyTemplateMapper templateMapper;

    public InternalNotifyService(NotifyDispatchService dispatchService,
                                 TenantConfigService configService,
                                 SysNotifyTemplateMapper templateMapper) {
        this.dispatchService = dispatchService;
        this.configService = configService;
        this.templateMapper = templateMapper;
    }

    /** 发送内部通知。 */
    public NotifyResponse notify(NotifyRequest req) {
        String channel = req == null || req.channel() == null
                ? "" : req.channel().trim().toUpperCase(Locale.ROOT);
        if (!SUPPORTED.contains(channel)) {
            return fail(channel, "不支持的渠道: " + req.channel() + "（可选 EMAIL/WECOM/DINGTALK）");
        }
        if (req.content() == null || req.content().isBlank()) {
            return fail(channel, "content 不能为空");
        }
        if ("EMAIL".equals(channel) && (req.recipient() == null || req.recipient().isBlank())) {
            return fail(channel, "EMAIL 渠道 recipient 不能为空");
        }
        try {
            String url = resolveWebhook(channel);
            NotifyDispatchService.DirectSendResult result = dispatchService.sendDirect(
                    channel, req.recipient(), req.subject(), req.content(), url);
            if (!"SENT".equals(result.status())) {
                log.warn("内部通知发送失败: channel={}, bizType={}, bizKey={}, reason={}",
                        channel, req.bizType(), req.bizKey(), result.failedReason());
                return new NotifyResponse(false, "FAILED", channel, "发送失败",
                        result.failedReason(), result.httpStatus());
            }
            return new NotifyResponse(true, "SENT", channel, "已发送", null, result.httpStatus());
        } catch (Exception e) {
            // 兜底：渠道/配置/序列化任何异常都不升级为 500
            log.error("内部通知异常: channel={}, bizKey={}", channel, req.bizKey(), e);
            return fail(channel, "发送异常: " + e.getMessage());
        }
    }

    private String resolveWebhook(String channel) {
        String configured = configService.findValue("notify." + channel.toLowerCase(Locale.ROOT) + ".webhook");
        if (configured != null && !configured.isBlank()) {
            return configured.trim();
        }
        List<SysNotifyTemplate> templates = templateMapper.selectList(
                new LambdaQueryWrapper<SysNotifyTemplate>()
                        .eq(SysNotifyTemplate::getChannel, channel)
                        .isNotNull(SysNotifyTemplate::getWebhookUrl)
                        .last("limit 5"));
        for (SysNotifyTemplate t : templates) {
            if (t.getWebhookUrl() != null && !t.getWebhookUrl().isBlank()) {
                return t.getWebhookUrl().trim();
            }
        }
        return null;
    }

    private NotifyResponse fail(String channel, String error) {
        return new NotifyResponse(false, "FAILED", channel, "发送失败", error, null);
    }
}
