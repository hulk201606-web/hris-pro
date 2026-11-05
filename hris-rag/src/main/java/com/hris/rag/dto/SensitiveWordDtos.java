package com.hris.rag.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

/**
 * 敏感词 DTO（KBD-F-017）。
 */
public final class SensitiveWordDtos {

    private SensitiveWordDtos() {
    }

    /** 新增敏感词；level 缺省 WARN，仅允许 BLOCK/WARN。 */
    public record SensitiveWordCreateRequest(
            @NotBlank(message = "word 不能为空") @Size(max = 64, message = "word 长度不能超过 64") String word,
            String level) {
    }

    /** 敏感词响应。 */
    public record SensitiveWordResponse(Long id, String word, String level, LocalDateTime createdAt) {
    }
}
