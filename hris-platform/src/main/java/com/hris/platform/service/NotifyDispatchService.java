package com.hris.platform.service;

import com.hris.common.exception.BizException;
import com.hris.common.exception.ErrorCode;
import com.hris.platform.dto.NotificationDtos.TestSendRequest;
import com.hris.platform.dto.NotificationDtos.TestSendResponse;
import com.hris.platform.entity.SysNotifyRecord;
import com.hris.platform.entity.SysNotifyTemplate;
import com.hris.platform.mapper.SysNotifyRecordMapper;
import com.hris.platform.mapper.SysNotifyTemplateMapper;
import com.hris.platform.util.JsonUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 通知分发（PLT-F-003 深化；PLT-F-004 接线偏好闸门）：渲染模板文本后按渠道 POST webhook 机器人。
 *
 * <p>支持渠道：钉钉机器人（msgtype=text）、企微机器人（msgtype=text）、通用 WEBHOOK。
 * 渲染复用 {@link NotifyTemplateService#renderText} 的占位符语义；失败不抛出到调用方，
 * 以 {@link TestSendResponse} 摘要表达，同时回写 sys_notify_record 状态（SENT/FAILED）。</p>
 *
 * <p>PLT-F-004 接线点：test-send 请求体可携带模拟目标 userId，发送前读
 * sys_notify_preference——该渠道对目标用户禁用（enabled=0）或处于免打扰时段
 * （支持跨午夜）则跳过发送，摘要 status=SKIPPED_PREFERENCE。当前系统无自动群发
 * 触发点，未来群发（批量按 userId 派发）应在每次实际投递前调用
 * {@link NotifyPreferenceService#skipReason} 复用同一闸门。</p>
 */
@Service
public class NotifyDispatchService {

    private static final Logger log = LoggerFactory.getLogger(NotifyDispatchService.class);

    private final SysNotifyTemplateMapper templateMapper;
    private final SysNotifyRecordMapper recordMapper;
    private final WebhookPoster poster;
    private final NotifyPreferenceService preferenceService;

    public NotifyDispatchService(SysNotifyTemplateMapper templateMapper,
                                 SysNotifyRecordMapper recordMapper,
                                 WebhookPoster poster,
                                 NotifyPreferenceService preferenceService) {
        this.templateMapper = templateMapper;
        this.recordMapper = recordMapper;
        this.poster = poster;
        this.preferenceService = preferenceService;
    }

    /**
     * 模板测试发送：渲染样例文本并发送到 webhook（body 可覆盖地址/指定模拟目标 userId）。
     * 目标不可用/SSRF 拒绝/网络失败均返回失败摘要，不抛异常；目标用户偏好禁用或
     * 免打扰时段返回 SKIPPED_PREFERENCE 摘要。
     */
    public TestSendResponse testSend(Long templateId, TestSendRequest req) {
        SysNotifyTemplate t = templateMapper.selectById(templateId);
        if (t == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "通知模板不存在");
        }
        // PLT-F-004：模拟目标用户的偏好闸门（未指定 userId 时跳过闸门，保持原测试语义）
        Long targetUser = req == null ? null : req.userId();
        if (targetUser != null) {
            String skip = preferenceService.skipReason(targetUser, t.getChannel(),
                    java.time.LocalTime.now());
            if (skip != null) {
                log.info("notify test-send skipped by preference: templateId={}, channel={}, userId={}",
                        t.getId(), t.getChannel(), targetUser);
                return new TestSendResponse(t.getId(), t.getChannel(), null,
                        "SKIPPED_PREFERENCE", null, null, skip);
            }
        }
        String url = req == null || req.webhookUrl() == null || req.webhookUrl().isBlank()
                ? t.getWebhookUrl() : req.webhookUrl().trim();
        String text = renderSample(t);
        if (url == null || url.isBlank()) {
            return fail(t.getId(), t.getChannel(), null, "模板未配置 webhook 地址且请求未覆盖");
        }
        try {
            checkSsrf(url);
        } catch (BizException e) {
            return fail(t.getId(), t.getChannel(), url, e.getMessage());
        }
        WebhookPoster.PostResult result = poster.postJson(url, buildPayload(t.getChannel(), text));
        writeRecord(t, url, result.success());
        if (result.success()) {
            log.info("notify test-send ok: templateId={}, channel={}, httpStatus={}",
                    t.getId(), t.getChannel(), result.httpStatus());
            return new TestSendResponse(t.getId(), t.getChannel(), url, "SENT",
                    result.httpStatus(), result.responseBody(), null);
        }
        return new TestSendResponse(t.getId(), t.getChannel(), url, "FAILED",
                result.httpStatus(), result.responseBody(), result.failedReason());
    }

    /** 渲染样例：无参数占位符原样保留，直观展示模板内容。 */
    private String renderSample(SysNotifyTemplate t) {
        String subject = t.getSubject() == null ? "" : t.getSubject();
        String content = t.getContent() == null ? "" : t.getContent();
        return subject.isBlank() ? content : subject + "\n" + content;
    }

    /** 按渠道构造机器人 payload。 */
    private String buildPayload(String channel, String text) {
        String ch = channel == null ? "" : channel.trim().toUpperCase();
        Map<String, Object> payload = new LinkedHashMap<>();
        switch (ch) {
            case "DINGTALK" -> payload.putAll(Map.of(
                    "msgtype", "text",
                    "text", Map.of("content", text)));
            case "WECOM" -> payload.putAll(Map.of(
                    "msgtype", "text",
                    "content", text));
            default -> payload.putAll(Map.of(
                    "msgtype", "text",
                    "content", text));
        }
        return JsonUtil.toJson(payload);
    }

    /**
     * SSRF 防线：仅允许 http/https；拒绝 localhost 与内网网段
     * （127./10./172.16-31./192.168.）及 0.0.0.0。按字面量主机判断（MVP 不做 DNS 解析复核）。
     */
    private void checkSsrf(String url) {
        URI uri;
        try {
            uri = URI.create(url);
        } catch (IllegalArgumentException e) {
            throw new BizException(ErrorCode.PARAM_INVALID, "webhook 地址非法");
        }
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase();
        if (!scheme.equals("http") && !scheme.equals("https")) {
            throw new BizException(ErrorCode.PARAM_INVALID, "webhook 地址仅允许 http/https");
        }
        String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase();
        if (host.isEmpty() || host.equals("localhost") || host.equals("0.0.0.0")
                || host.startsWith("127.") || host.startsWith("10.")
                || host.startsWith("192.168.") || host.endsWith(".local") || host.endsWith(".internal")) {
            throw new BizException(ErrorCode.PARAM_INVALID, "webhook 地址不允许指向内网: " + host);
        }
        if (host.startsWith("172.")) {
            int secondOctet = parseSecondOctet(host);
            if (secondOctet >= 16 && secondOctet <= 31) {
                throw new BizException(ErrorCode.PARAM_INVALID, "webhook 地址不允许指向内网: " + host);
            }
        }
    }

    private int parseSecondOctet(String host) {
        String[] parts = host.split("\\.");
        try {
            return parts.length >= 2 ? Integer.parseInt(parts[1]) : -1;
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    /** 回写通知记录：成功 SENT + sentAt，失败 FAILED（记录本身落库失败仅告警，不影响摘要返回）。 */
    private void writeRecord(SysNotifyTemplate t, String url, boolean success) {
        try {
            SysNotifyRecord r = new SysNotifyRecord();
            r.setTemplateId(t.getId());
            r.setChannel(t.getChannel());
            r.setSendStatus(success ? "SENT" : "FAILED");
            if (success) {
                r.setSentAt(LocalDateTime.now());
            }
            recordMapper.insert(r);
        } catch (Exception e) {
            log.warn("notify record write-back failed: templateId={}, url={}: {}",
                    t.getId(), url, e.getMessage());
        }
    }

    private TestSendResponse fail(Long templateId, String channel, String url, String reason) {
        writeRecordFailed(templateId, channel);
        return new TestSendResponse(templateId, channel, url, "FAILED", null, null, reason);
    }

    private void writeRecordFailed(Long templateId, String channel) {
        try {
            SysNotifyRecord r = new SysNotifyRecord();
            r.setTemplateId(templateId);
            r.setChannel(channel);
            r.setSendStatus("FAILED");
            recordMapper.insert(r);
        } catch (Exception e) {
            log.warn("notify record write-back failed: templateId={}", templateId, e);
        }
    }
}
