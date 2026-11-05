package com.hris.org.enums;

/**
 * 组织版本状态（批 11 #23 / ORG-F-004，docs/20 §4.3 差距收敛）。
 *
 * <p>状态机：创建即 {@link #DRAFT}（生效日未到）；到生效日由调度（或管理端手动）
 * 激活为 {@link #ACTIVE}，同一租户同一时刻只允许一个 ACTIVE；被新版本替代的
 * 原 ACTIVE 置 {@link #SUPERSEDED}（回滚 = 重新激活历史版本，同样触发本流转）。</p>
 */
public enum OrgVersionStatus {

    /** 草稿：仅管理端（org:dept:write）可见 */
    DRAFT,
    /** 生效：对外可见的当前版本 */
    ACTIVE,
    /** 已替代：历史版本，只读可查 */
    SUPERSEDED;

    /** 宽松解析（大小写不敏感），非法值返回 null 由调用方决定兜底。 */
    public static OrgVersionStatus from(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        for (OrgVersionStatus s : values()) {
            if (s.name().equalsIgnoreCase(value.trim())) {
                return s;
            }
        }
        return null;
    }
}
