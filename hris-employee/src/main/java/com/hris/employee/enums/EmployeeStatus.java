package com.hris.employee.enums;

/**
 * 员工在职状态（docs/03-02 §4 状态机）。
 *
 * <p>持久化到 emp_employee.status（TINYINT）。相较 DDL 注释的四值枚举（0 待入职 / 1 在职 / 2 离职 / 3 黑名单），
 * 本枚举按状态机细化为 8 态，code 与 DDL 注释存在差异，落库仍为 TINYINT，无 schema 变更。
 * 终态（RESIGNED/CANCELLED/RETIRED/BLACKLISTED）触发状态日志与领域事件（MVP 先落日志）。</p>
 */
public enum EmployeeStatus {

    /** 待入职 */
    PENDING(0, "待入职"),
    /** 试用 */
    PROBATION(1, "试用"),
    /** 在职 */
    ACTIVE(2, "在职"),
    /** 离职中（离职流程通过，最后工作日前） */
    RESIGNING(3, "离职中"),
    /** 已离职（终态） */
    RESIGNED(4, "已离职"),
    /** 已取消（取消入职，终态） */
    CANCELLED(5, "已取消"),
    /** 退休（终态） */
    RETIRED(6, "退休"),
    /** 黑名单（终态标记） */
    BLACKLISTED(7, "黑名单");

    private final int code;
    private final String label;

    EmployeeStatus(int code, String label) {
        this.code = code;
        this.label = label;
    }

    /**
     * 状态机合法流转判定（docs/03-02 §4）。非法流转由调用方抛异常拦截。
     */
    public boolean canTransitionTo(EmployeeStatus target) {
        if (target == null || target == this) {
            return false;
        }
        return switch (this) {
            case PENDING -> target == PROBATION || target == CANCELLED;
            case PROBATION -> target == ACTIVE || target == RESIGNING || target == BLACKLISTED;
            case ACTIVE -> target == RESIGNING || target == RETIRED || target == BLACKLISTED;
            case RESIGNING -> target == RESIGNED || target == ACTIVE;
            case RESIGNED -> target == BLACKLISTED;
            case CANCELLED, RETIRED, BLACKLISTED -> false;
        };
    }

    /** 终态（不可再流转）。 */
    public boolean isTerminal() {
        return switch (this) {
            case RESIGNED, CANCELLED, RETIRED, BLACKLISTED -> true;
            default -> false;
        };
    }

    public int getCode() {
        return code;
    }

    public String getLabel() {
        return label;
    }

    public static EmployeeStatus fromCode(Integer code) {
        if (code == null) {
            return null;
        }
        for (EmployeeStatus s : values()) {
            if (s.code == code) {
                return s;
            }
        }
        return null;
    }
}
