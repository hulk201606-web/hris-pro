package com.hris.recruit.dto;

/**
 * 编制校验结果（RCS 需求提交前置，契约对齐 org /internal/org/hc/check）。
 */
public final class HcCheckDtos {

    private HcCheckDtos() {
    }

    /** allowed=可用额度是否覆盖申请人数；shortfall=缺口人数。 */
    public record HcCheckResponse(boolean allowed, Integer quota, Integer occupied, Integer shortfall) {
    }
}
