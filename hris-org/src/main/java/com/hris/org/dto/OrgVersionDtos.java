package com.hris.org.dto;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 组织版本 DTO（批 8 #16 MVP；批 11 #23 扩展状态机与生效日）。
 */
public final class OrgVersionDtos {

    private OrgVersionDtos() {
    }

    /**
     * 版本摘要（创建/激活/回滚返回值与版本列表项）。
     *
     * @param status        DRAFT / ACTIVE / SUPERSEDED
     * @param effectiveFrom 计划生效日（null = 立即）
     */
    public record OrgVersionSummary(String versionNo, String snapshotDate,
                                    long positionCount, long gradeCount,
                                    String status, String effectiveFrom) {
    }

    /** 版本详情（岗位/职级两个维度的快照内容 + 状态）。 */
    public record OrgVersionDetail(String versionNo, String snapshotDate,
                                   String status, String effectiveFrom,
                                   List<Map<String, Object>> positions,
                                   List<Map<String, Object>> grades) {
    }

    /** 创建版本请求（body 可缺省，缺省 = 立即生效日今天）。 */
    public record OrgVersionCreateRequest(LocalDate effectiveFrom) {
    }

    /** 到期激活结果（调度中心日任务核对用）。 */
    public record ActivateDueResponse(LocalDate effectiveDate, int activated) {
    }
}
