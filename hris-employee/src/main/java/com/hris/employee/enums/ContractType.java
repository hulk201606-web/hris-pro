package com.hris.employee.enums;

/**
 * 合同类型（emp_contract.type）。
 */
public enum ContractType {

    /** 固定期限 */
    FIXED("FIXED"),
    /** 无固定期限 */
    OPEN_ENDED("OPEN_ENDED"),
    /** 劳务合同（EMP-F-014 深化：劳务派遣/外包用工，非劳动关系合同台账） */
    LABOR("LABOR"),
    /** 实习协议 */
    INTERN("INTERN");

    private final String code;

    ContractType(String code) {
        this.code = code;
    }

    public String getCode() {
        return code;
    }

    public static ContractType fromCode(String code) {
        for (ContractType t : values()) {
            if (t.code.equals(code)) {
                return t;
            }
        }
        return null;
    }
}
