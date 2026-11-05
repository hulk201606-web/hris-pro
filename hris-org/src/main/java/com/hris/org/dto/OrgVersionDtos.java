package com.hris.org.dto;

import java.util.List;
import java.util.Map;

/**
 * 组织版本 DTO（批 8 #16 MVP：组织版本扩展到岗位/职级）。
 */
public final class OrgVersionDtos {

    private OrgVersionDtos() {
    }

    /** 版本摘要（创建/回滚返回值与版本列表项）。 */
    public record OrgVersionSummary(String versionNo, String snapshotDate,
                                    long positionCount, long gradeCount) {
    }

    /** 版本详情（岗位/职级两个维度的快照内容）。 */
    public record OrgVersionDetail(String versionNo, String snapshotDate,
                                   List<Map<String, Object>> positions,
                                   List<Map<String, Object>> grades) {
    }
}
