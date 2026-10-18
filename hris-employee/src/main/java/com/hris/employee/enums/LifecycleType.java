package com.hris.employee.enums;

/**
 * 生命周期单类型（emp_lifecycle_case.type）。对应流程模型 EMP_ONBOARD / EMP_REGULAR / EMP_TRANSFER / EMP_RESIGN。
 */
public enum LifecycleType {

    /** 入职 */
    ONBOARD("ONBOARD"),
    /** 转正 */
    REGULAR("REGULAR"),
    /** 调动 */
    TRANSFER("TRANSFER"),
    /** 离职 */
    RESIGN("RESIGN");

    private final String code;

    LifecycleType(String code) {
        this.code = code;
    }

    public String getCode() {
        return code;
    }

    public static LifecycleType fromCode(String code) {
        for (LifecycleType t : values()) {
            if (t.code.equals(code)) {
                return t;
            }
        }
        return null;
    }
}
