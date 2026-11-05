package com.hris.platform.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hris.common.exception.BizException;
import com.hris.common.exception.ErrorCode;
import com.hris.platform.dto.NotificationDtos.RenderResponse;
import com.hris.platform.dto.NotificationDtos.TemplateRequest;
import com.hris.platform.dto.NotificationDtos.TemplateResponse;
import com.hris.platform.entity.SysNotifyTemplate;
import com.hris.platform.mapper.SysNotifyTemplateMapper;
import com.hris.platform.util.CodeUtil;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * 通知模板管理 + 集中渲染（PLT-F-003，docs/03-09 §4：渲染在 platform 完成，业务服务只发事件+参数）。
 */
@Service
public class NotifyTemplateService {

    private final SysNotifyTemplateMapper mapper;

    public NotifyTemplateService(SysNotifyTemplateMapper mapper) {
        this.mapper = mapper;
    }

    /** 按渠道查询通知模板列表。 */
    public List<TemplateResponse> list(String channel) {
        LambdaQueryWrapper<SysNotifyTemplate> qw = new LambdaQueryWrapper<>();
        if (channel != null && !channel.isBlank()) {
            qw.eq(SysNotifyTemplate::getChannel, channel.trim().toUpperCase());
        }
        qw.orderByAsc(SysNotifyTemplate::getCode);
        return mapper.selectList(qw).stream().map(this::toResponse).toList();
    }

    /** 新建通知模板。 */
    public TemplateResponse create(TemplateRequest req) {
        String code = CodeUtil.normalize(req.code());
        String channel = normalizeChannel(req.channel());
        ensureAvailable(code, channel);
        SysNotifyTemplate e = new SysNotifyTemplate();
        e.setCode(code);
        e.setChannel(channel);
        e.setSubject(req.subject());
        e.setContent(req.content());
        e.setWebhookUrl(req.webhookUrl());
        mapper.insert(e);
        return toResponse(e);
    }

    /** 更新通知模板（乐观锁防并发覆盖）。 */
    public TemplateResponse update(Long id, TemplateRequest req) {
        SysNotifyTemplate e = require(id);
        if (req.subject() != null) {
            e.setSubject(req.subject());
        }
        if (req.content() != null) {
            e.setContent(req.content());
        }
        if (req.webhookUrl() != null) {
            e.setWebhookUrl(req.webhookUrl().trim());
        }
        mapper.updateById(e);
        return toResponse(e);
    }

    /** 删除通知模板（逻辑删除）。 */
    public void delete(Long id) {
        require(id);
        mapper.deleteById(id);
    }

    /** 模板集中渲染：按 code+channel 取模板，{param} 占位符替换。 */
    public RenderResponse render(String code, String channel, Map<String, Object> params) {
        SysNotifyTemplate t = requireByCodeAndChannel(CodeUtil.normalize(code), normalizeChannel(channel));
        String subject = renderText(t.getSubject(), params);
        String content = renderText(t.getContent(), params);
        return new RenderResponse(subject, content);
    }

    private SysNotifyTemplate requireByCodeAndChannel(String code, String channel) {
        SysNotifyTemplate t = mapper.selectOne(new LambdaQueryWrapper<SysNotifyTemplate>()
                .eq(SysNotifyTemplate::getCode, code)
                .eq(SysNotifyTemplate::getChannel, channel));
        if (t == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "通知模板不存在");
        }
        return t;
    }

    private String renderText(String template, Map<String, Object> params) {
        if (template == null) {
            return null;
        }
        String out = template;
        if (params != null) {
            for (Map.Entry<String, Object> e : params.entrySet()) {
                out = out.replace("{" + e.getKey() + "}", e.getValue() == null ? "" : e.getValue().toString());
            }
        }
        return out;
    }

    private void ensureAvailable(String code, String channel) {
        Long cnt = mapper.selectCount(new LambdaQueryWrapper<SysNotifyTemplate>()
                .eq(SysNotifyTemplate::getCode, code)
                .eq(SysNotifyTemplate::getChannel, channel));
        if (cnt != null && cnt > 0) {
            throw new BizException(ErrorCode.PARAM_INVALID, "同渠道模板编码已存在");
        }
    }

    private SysNotifyTemplate require(Long id) {
        SysNotifyTemplate e = mapper.selectById(id);
        if (e == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "通知模板不存在");
        }
        return e;
    }

    private String normalizeChannel(String channel) {
        return channel == null ? null : channel.trim().toUpperCase();
    }

    private TemplateResponse toResponse(SysNotifyTemplate e) {
        return new TemplateResponse(e.getId(), e.getCode(), e.getChannel(), e.getSubject(), e.getContent(),
                e.getWebhookUrl(), e.getUpdatedAt());
    }
}
