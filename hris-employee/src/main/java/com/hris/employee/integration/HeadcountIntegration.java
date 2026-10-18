package com.hris.employee.integration;

import com.hris.common.api.R;
import com.hris.common.exception.BizException;
import com.hris.common.exception.ErrorCode;
import com.hris.employee.client.OrgClient;
import com.hris.employee.dto.HcCheckResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 组织域集成门面（EMP 编制校验）。
 *
 * <p>编制校验为入职/调动的前置硬校验：编制不足抛 51204。org 服务不可用或返回异常时
 * fail-closed 抛 SYSTEM_ERROR，不静默放行（超编入职/调动必须拦截，docs/07 错误码 51204）。</p>
 */
@Service
public class HeadcountIntegration {

    private static final Logger log = LoggerFactory.getLogger(HeadcountIntegration.class);

    private final OrgClient orgClient;

    public HeadcountIntegration(OrgClient orgClient) {
        this.orgClient = orgClient;
    }

    /**
     * 编制校验。org 不可用或异常时抛 BizException（fail-closed），不返回 null 降级。
     */
    public HcCheckResponse check(Long deptId, Long positionId, Integer count) {
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
        return resp.getData();
    }
}
