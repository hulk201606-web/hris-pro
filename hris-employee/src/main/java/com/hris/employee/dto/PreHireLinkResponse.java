package com.hris.employee.dto;

import java.time.LocalDateTime;

/**
 * 入职资料自助填报链接（EMP-F-005，扫码履历 token，无需账号）。
 */
public record PreHireLinkResponse(Long empId, String token, String link, LocalDateTime expireAt) {
}
