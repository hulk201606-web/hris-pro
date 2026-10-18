package com.hris.employee.enums;

/**
 * 待生效事务类型（emp_pending_transaction.trans_type）。
 *
 * <p>DDL 注释仅列 TRANSFER/RESIGN/PROMOTE，按 docs/03-02 §2 状态机，入职/转正也走待生效事务队列，
 * 故此处扩展 ONBOARD/REGULAR 两类；列宽 VARCHAR(16) 无 schema 变更。</p>
 */
public enum PendingTransType {

    /** 入职生效 */
    ONBOARD("ONBOARD"),
    /** 转正生效 */
    REGULAR("REGULAR"),
    /** 调动生效 */
    TRANSFER("TRANSFER"),
    /** 离职生效 */
    RESIGN("RESIGN"),
    /** 晋升生效（EMP-F-010，P1 预留） */
    PROMOTE("PROMOTE");

    private final String code;

    PendingTransType(String code) {
        this.code = code;
    }

    public String getCode() {
        return code;
    }

    public static PendingTransType fromCode(String code) {
        for (PendingTransType t : values()) {
            if (t.code.equals(code)) {
                return t;
            }
        }
        return null;
    }
}
