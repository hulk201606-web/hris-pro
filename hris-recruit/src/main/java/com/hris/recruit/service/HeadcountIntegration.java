package com.hris.recruit.service;

import com.hris.common.api.R;
import com.hris.common.exception.BizException;
import com.hris.common.exception.ErrorCode;
import com.hris.recruit.client.OrgClient;
import com.hris.recruit.dto.HcCheckDtos.HcCheckResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 招聘需求编制校验门面（RCS-F-022 审批接线配套，docs/03-04 关键规则「需求人数 ≤ 编制缺口」）。
 *
 * <p>fail-closed（同 employee HeadcountIntegration）：org 不可用或返回异常直接拒绝提交，
 * 不静默放行超编需求；编制不足抛 EMP_HEADCOUNT_SHORT(51204) 并透出缺口人数。</p>
 */
@Service
public class HeadcountIntegration {

    private static final Logger log = LoggerFactory.getLogger(HeadcountIntegration.class);

    private final OrgClient orgClient;

    public HeadcountIntegration(OrgClient orgClient) {
        this.orgClient = orgClient;
    }

    /** 校验 dept+position 下申请 count 人的编制额度；不足或服务不可用时抛 BizException。 */
    public void check(Long deptId, Long positionId, Integer count) {
        if (count == null || count <= 0) {
            return;
        }
        R<HcCheckResponse> resp;
        try {
            resp = orgClient.check(deptId, positionId, count);
        } catch (Exception e) {
            log.error("编制校验服务不可用: dept={}, position={}, err={}", deptId, positionId, e.getMessage());
            throw new BizException(ErrorCode.SYSTEM_ERROR, "编制校验服务不可用，请稍后重试");
        }
        if (resp == null || !resp.isSuccess() || resp.getData() == null) {
            log.warn("编制校验服务返回异常: dept={}, position={}, code={}",
                    deptId, positionId, resp == null ? "null" : resp.getCode());
            throw new BizException(ErrorCode.SYSTEM_ERROR, "编制校验服务不可用，请稍后重试");
        }
        HcCheckResponse data = resp.getData();
        if (!data.allowed()) {
            int shortfall = data.shortfall() == null ? 0 : data.shortfall();
            throw new BizException(ErrorCode.EMP_HEADCOUNT_SHORT,
                    "编制不足，缺口 " + shortfall + " 人");
        }
    }
}
