package com.hris.platform.controller;

import com.hris.common.api.R;
import com.hris.common.exception.BizException;
import com.hris.common.exception.ErrorCode;
import com.hris.common.security.AuthContextHolder;
import com.hris.platform.dto.NotifyPreferenceDtos.PreferenceRequest;
import com.hris.platform.dto.NotifyPreferenceDtos.PreferenceResponse;
import com.hris.platform.service.NotifyPreferenceService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 通知渠道偏好端点（PLT-F-004）。
 *
 * <p>权限决策（/me self 语义）：照既有自助端点范式（如 att LeaveQuotaController /me）
 * 不加 @RequirePerm——登录用户只能读写自己的偏好行，身份取网关透传的
 * AuthContextHolder.getUserId()，缺失即 20401 拒绝；无租户内越权面。</p>
 */
@RestController
@RequestMapping("/api/v1/plt/notify-preferences")
public class NotifyPreferenceController {

    private final NotifyPreferenceService preferenceService;

    public NotifyPreferenceController(NotifyPreferenceService preferenceService) {
        this.preferenceService = preferenceService;
    }

    /** 我的渠道偏好列表（未设置过的渠道不在列表中，语义为默认启用）。 */
    @GetMapping("/me")
    public R<List<PreferenceResponse>> mine() {
        return R.ok(preferenceService.listMine(requireUser()));
    }

    /** 单渠道偏好 upsert：body {channel, enabled, quietStart?, quietEnd?}。 */
    @PutMapping("/me")
    public R<PreferenceResponse> upsertMine(@Valid @RequestBody PreferenceRequest req) {
        return R.ok(preferenceService.upsertMine(requireUser(), req));
    }

    private Long requireUser() {
        Long userId = AuthContextHolder.getUserId();
        if (userId == null || userId == 0L) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "无法识别当前用户身份");
        }
        return userId;
    }
}
