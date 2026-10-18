package com.hris.employee.enums;

/**
 * 合同类型（emp_contract.type）。
 */
public enum ContractType {

    /** 固定期限 */
    FIXED("FIXED"),
    /** 无固定期限 */
    OPEN_ENDED("OPEN_ENDED"),
    /** 实习/劳务 */
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
