package com.hris.employee.integration;

import com.hris.common.api.R;
import com.hris.common.exception.BizException;
import com.hris.common.exception.ErrorCode;
import com.hris.employee.client.OrgClient;
import com.hris.employee.dto.HcCheckResponse;
import com.hris.employee.dto.HcOccupancyRequest;
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

    /**
     * 编制占用回写（§10.5-B8）。与编制校验不同，回写为 fail-open：
     * org 不可用或返回异常时仅 log.warn，不阻断事务落账——占用看板偏差可由
     * 占用流水（transNo 幂等）对账修复，而入转调离落账失败会造成账实错乱。
     */
    public void writeback(HcOccupancyRequest req) {
        try {
            R<Void> resp = orgClient.occupancy(req);
            if (resp == null || !resp.isSuccess()) {
                log.warn("编制占用回写返回异常（fail-open 不阻断落账）: transNo={}, code={}",
                        req.transNo(), resp == null ? "null" : resp.getCode());
            }
        } catch (Exception e) {
            log.warn("编制占用回写服务不可用（fail-open 不阻断落账）: transNo={}, err={}",
                    req.transNo(), e.getMessage());
        }
    }
}
