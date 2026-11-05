package com.hris.recruit.dto;

import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;

/**
 * ResumeParse DTO（RCS-F-007）。
 */
public final class ResumeParseDtos {

    private ResumeParseDtos() {
    }

    /** 解析请求：简历以文件引用存储（rcu_resume 仅 file_id），解析输入为调用方提供的纯文本。 */
    public record ResumeParseRequest(String text) {
    }

    /** 人工修正请求：body 可改任意 parsed 字段，未传字段不更新。 */
    public record ResumeParseUpdateRequest(
            @Size(max = 64, message = "parsedName 长度不能超过 64") String parsedName,
            @Size(max = 32, message = "parsedMobile 长度不能超过 32") String parsedMobile,
            @Size(max = 128, message = "parsedEmail 长度不能超过 128") String parsedEmail,
            String educationJson,
            String experienceJson) {
    }

    /** 解析结果响应。 */
    public record ResumeParseResponse(
            Long id,
            Long resumeId,
            Long candidateId,
            String parsedName,
            String parsedMobile,
            String parsedEmail,
            String educationJson,
            String experienceJson,
            String parseStatus,
            String parseError,
            LocalDateTime createdAt,
            LocalDateTime updatedAt) {
    }
}
