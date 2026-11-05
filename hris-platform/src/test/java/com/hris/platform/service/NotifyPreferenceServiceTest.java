package com.hris.platform.service;

import com.hris.common.exception.BizException;
import com.hris.common.exception.ErrorCode;
import com.hris.platform.dto.NotifyPreferenceDtos.PreferenceRequest;
import com.hris.platform.dto.NotifyPreferenceDtos.PreferenceResponse;
import com.hris.platform.entity.SysNotifyPreference;
import com.hris.platform.mapper.SysNotifyPreferenceMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 通知渠道偏好单元测试（PLT-F-004）：upsert、渠道校验、禁用跳过、免打扰跨午夜。
 */
@ExtendWith(MockitoExtension.class)
class NotifyPreferenceServiceTest {

    @Mock
    private SysNotifyPreferenceMapper preferenceMapper;

    private NotifyPreferenceService service;

    @BeforeEach
    void setUp() {
        service = new NotifyPreferenceService(preferenceMapper);
    }

    private SysNotifyPreference pref(Long userId, String channel, int enabled,
                                     LocalTime start, LocalTime end) {
        SysNotifyPreference p = new SysNotifyPreference();
        p.setId(1L);
        p.setUserId(userId);
        p.setChannel(channel);
        p.setEnabled(enabled);
        p.setQuietStart(start);
        p.setQuietEnd(end);
        return p;
    }

    @Test
    @DisplayName("upsert：uk 不存在则插入，channel 归一大写")
    void upsertShouldInsertWhenAbsent() {
        when(preferenceMapper.selectOne(any())).thenReturn(null);

        PreferenceResponse resp = service.upsertMine(5L,
                new PreferenceRequest("dingtalk", 1, LocalTime.of(22, 0), LocalTime.of(8, 0)));

        assertEquals("DINGTALK", resp.channel());
        assertEquals(1, resp.enabled());
        ArgumentCaptor<SysNotifyPreference> captor = ArgumentCaptor.forClass(SysNotifyPreference.class);
        verify(preferenceMapper).insert(captor.capture());
        assertEquals("DINGTALK", captor.getValue().getChannel());
        assertEquals(5L, captor.getValue().getUserId());
    }

    @Test
    @DisplayName("upsert：uk 已存在则更新原行")
    void upsertShouldUpdateWhenExists() {
        SysNotifyPreference existing = pref(5L, "WECOM", 1, null, null);
        when(preferenceMapper.selectOne(any())).thenReturn(existing);

        PreferenceResponse resp = service.upsertMine(5L,
                new PreferenceRequest("WECOM", 0, null, null));

        assertEquals(0, resp.enabled());
        verify(preferenceMapper).updateById(existing);
        verify(preferenceMapper, never()).insert(any(SysNotifyPreference.class));
    }

    @Test
    @DisplayName("upsert：非法渠道 → PARAM_INVALID")
    void upsertShouldRejectUnknownChannel() {
        BizException e = assertThrows(BizException.class, () -> service.upsertMine(5L,
                new PreferenceRequest("SMS", 1, null, null)));
        assertEquals(ErrorCode.PARAM_INVALID.getCode(), e.getCode());
    }

    @Test
    @DisplayName("偏好闸门：渠道禁用（enabled=0）→ 返回跳过原因")
    void skipReasonShouldSkipWhenDisabled() {
        when(preferenceMapper.selectOne(any()))
                .thenReturn(pref(5L, "DINGTALK", 0, null, null));

        String reason = service.skipReason(5L, "DINGTALK", LocalTime.of(12, 0));

        assertEquals(true, reason != null && reason.startsWith("SKIPPED_PREFERENCE"));
    }

    @Test
    @DisplayName("偏好闸门：免打扰时段内（同时段 12:00-13:00，now=12:30）→ 跳过")
    void skipReasonShouldSkipInQuietHours() {
        when(preferenceMapper.selectOne(any())).thenReturn(
                pref(5L, "WECOM", 1, LocalTime.of(12, 0), LocalTime.of(13, 0)));

        String reason = service.skipReason(5L, "WECOM", LocalTime.of(12, 30));

        assertEquals(true, reason != null && reason.contains("免打扰"));
    }

    @Test
    @DisplayName("偏好闸门：quiet 跨午夜（22:00-08:00），now=23:00 或 07:00 跳过，12:00 放行")
    void skipReasonShouldSupportCrossMidnightQuiet() {
        when(preferenceMapper.selectOne(any())).thenReturn(
                pref(5L, "WEBHOOK", 1, LocalTime.of(22, 0), LocalTime.of(8, 0)));

        assertEquals(true, service.skipReason(5L, "WEBHOOK", LocalTime.of(23, 0)) != null);
        assertEquals(true, service.skipReason(5L, "WEBHOOK", LocalTime.of(7, 0)) != null);
        assertNull(service.skipReason(5L, "WEBHOOK", LocalTime.of(12, 0)));
    }

    @Test
    @DisplayName("偏好闸门：未设置偏好的用户放行（返回 null）")
    void skipReasonShouldAllowWhenNoPreference() {
        when(preferenceMapper.selectOne(any())).thenReturn(null);

        assertNull(service.skipReason(5L, "DINGTALK", LocalTime.of(9, 0)));
    }

    @Test
    @DisplayName("listMine：返回当前用户全部偏好行")
    void listMineShouldReturnRows() {
        when(preferenceMapper.selectList(any())).thenReturn(
                List.of(pref(5L, "DINGTALK", 1, null, null)));

        List<PreferenceResponse> rows = service.listMine(5L);

        assertEquals(1, rows.size());
        assertEquals("DINGTALK", rows.get(0).channel());
    }
}
