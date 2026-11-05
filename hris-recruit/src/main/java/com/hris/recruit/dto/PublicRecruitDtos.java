package com.hris.recruit.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * 招聘公开端点 + 漏斗报表 DTO（RCS-F-004 / RCS-F-011 / RCS-F-020）。
 *
 * <p>公开出参仅含非敏感字段：职位以需求单为主（reqNo/positionId/deptId/headcount），
 * 不含需求原因、审批链等内部信息；候选人手机号永不出参（仅落库）。</p>
 */
public final class PublicRecruitDtos {

    private PublicRecruitDtos() {
    }

    /** 公开职位项：OPEN + published=1 的需求单。title 来自关联职位（无职位记录时为 null）。 */
    public record PublicJobItem(Long reqId, String reqNo, String title, Long deptId,
                                Integer headcount, java.time.LocalDateTime createdAt) {
    }

    /** 官网免登录投递请求体。 */
    public record PublicApplyRequest(
            @NotBlank(message = "name 不能为空") @Size(max = 64, message = "name 最长 64 字符") String name,
            @NotBlank(message = "mobile 不能为空") String mobile,
            @NotBlank(message = "email 不能为空") @Email(message = "email 格式不合法") @Size(max = 128, message = "email 最长 128 字符") String email,
            @Size(max = 500, message = "selfIntro 最长 500 字符") String selfIntro) {
    }

    /** 投递受理结果：仅回执应用侧标识，不外泄候选人主键以外的信息。 */
    public record PublicApplyResponse(Long applicationId, String message) {
    }

    /** 漏斗报表行：stage 阶段、count 去重候选人数、conversion 相对 APPLIED 的转化率（百分比，2 位小数）。 */
    public record FunnelItem(String stage, long count, BigDecimal conversion) {
    }
}
