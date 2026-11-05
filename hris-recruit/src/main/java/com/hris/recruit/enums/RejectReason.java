package com.hris.recruit.enums;

/**
 * 淘汰原因枚举（RCS-F-012）：结构化初筛淘汰原因，OTHER 必须补充 note。
 */
public enum RejectReason {

    /** 技能不匹配 */
    SKILL_MISMATCH(false),
    /** 薪资不匹配 */
    SALARY_MISMATCH(false),
    /** 态度/意愿不符 */
    ATTITUDE(false),
    /** 编制关闭 */
    HEADCOUNT_CLOSED(false),
    /** 候选人主动撤回 */
    WITHDRAWN(false),
    /** 其他（必须填写 note） */
    OTHER(true);

    private final boolean noteRequired;

    RejectReason(boolean noteRequired) {
        this.noteRequired = noteRequired;
    }

    /** 该原因是否必须补充说明。 */
    public boolean isNoteRequired() {
        return noteRequired;
    }

    /** 严格解析，非法值抛 IllegalArgumentException 由调用方转 PARAM_INVALID。 */
    public static RejectReason parse(String value) {
        return RejectReason.valueOf(value.trim().toUpperCase());
    }
}
