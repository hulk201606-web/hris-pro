package com.hris.platform.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hris.common.exception.BizException;
import com.hris.common.exception.ErrorCode;
import com.hris.platform.dto.NotifyPreferenceDtos.PreferenceRequest;
import com.hris.platform.dto.NotifyPreferenceDtos.PreferenceResponse;
import com.hris.platform.entity.SysNotifyPreference;
import com.hris.platform.mapper.SysNotifyPreferenceMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalTime;
import java.util.List;
import java.util.Set;

/**
 * 通知渠道偏好服务（PLT-F-004）：个人维度按渠道启停 + 免打扰时段，
 * 供 NotifyDispatchService 发送前做偏好闸门（禁用/quiet 跳过）。
 */
@Service
public class NotifyPreferenceService {

    public static final Set<String> SUPPORTED_CHANNELS = Set.of("DINGTALK", "WECOM", "WEBHOOK");

    private final SysNotifyPreferenceMapper preferenceMapper;

    public NotifyPreferenceService(SysNotifyPreferenceMapper preferenceMapper) {
        this.preferenceMapper = preferenceMapper;
    }

    /** 我的渠道偏好列表（未设置过的渠道不在列表中，语义为默认启用）。 */
    public List<PreferenceResponse> listMine(Long userId) {
        return preferenceMapper.selectList(new LambdaQueryWrapper<SysNotifyPreference>()
                        .eq(SysNotifyPreference::getUserId, userId))
                .stream().map(this::toResponse).toList();
    }

    /** 单渠道偏好 upsert（uk tenant_id+user_id+channel，存在则更新）。 */
    @Transactional(rollbackFor = Exception.class)
    public PreferenceResponse upsertMine(Long userId, PreferenceRequest req) {
        String channel = normalizeChannel(req.channel());
        validateQuietRange(req);
        SysNotifyPreference existing = preferenceMapper.selectOne(new LambdaQueryWrapper<SysNotifyPreference>()
                .eq(SysNotifyPreference::getUserId, userId)
                .eq(SysNotifyPreference::getChannel, channel));
        SysNotifyPreference p = existing == null ? new SysNotifyPreference() : existing;
        p.setUserId(userId);
        p.setChannel(channel);
        p.setEnabled(req.enabled());
        p.setQuietStart(req.quietStart());
        p.setQuietEnd(req.quietEnd());
        if (existing == null) {
            preferenceMapper.insert(p);
        } else {
            preferenceMapper.updateById(p);
        }
        return toResponse(p);
    }

    /**
     * 发送前偏好闸门：该用户该渠道禁用（enabled=0）或处于免打扰时段（now ∈
     * [quiet_start, quiet_end)，支持跨午夜）时返回跳过原因；允许发送返回 null。
     * 用户未设置偏好视为允许。
     */
    public String skipReason(Long userId, String channel, LocalTime now) {
        if (userId == null) {
            return null;
        }
        SysNotifyPreference p = preferenceMapper.selectOne(new LambdaQueryWrapper<SysNotifyPreference>()
                .eq(SysNotifyPreference::getUserId, userId)
                .eq(SysNotifyPreference::getChannel, channel));
        if (p == null) {
            return null;
        }
        if (p.getEnabled() != null && p.getEnabled() == 0) {
            return "SKIPPED_PREFERENCE: 渠道对该用户已禁用";
        }
        if (inQuietHours(p, now)) {
            return "SKIPPED_PREFERENCE: 处于免打扰时段";
        }
        return null;
    }

    /**
     * 免打扰判定：quiet_start/quiet_end 均非空才生效；start&lt;=end 为同时段
     * [start, end)；start&gt;end 为跨午夜 [start, 24:00) ∪ [00:00, end)。
     */
    private boolean inQuietHours(SysNotifyPreference p, LocalTime now) {
        LocalTime start = p.getQuietStart();
        LocalTime end = p.getQuietEnd();
        if (start == null || end == null || now == null) {
            return false;
        }
        if (start.isBefore(end)) {
            return !now.isBefore(start) && now.isBefore(end);
        }
        if (start.isAfter(end)) {
            return !now.isBefore(start) || now.isBefore(end);
        }
        return false;
    }

    private void validateQuietRange(PreferenceRequest req) {
        if (req.quietStart() != null && req.quietEnd() != null && req.quietStart().equals(req.quietEnd())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "quietStart 与 quietEnd 不能相同");
        }
    }

    private String normalizeChannel(String channel) {
        String ch = channel == null ? "" : channel.trim().toUpperCase();
        if (!SUPPORTED_CHANNELS.contains(ch)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "channel 仅支持 DINGTALK/WECOM/WEBHOOK");
        }
        return ch;
    }

    private PreferenceResponse toResponse(SysNotifyPreference p) {
        return new PreferenceResponse(p.getId(), p.getUserId(), p.getChannel(),
                p.getEnabled(), p.getQuietStart(), p.getQuietEnd(), p.getUpdatedAt());
    }
}
