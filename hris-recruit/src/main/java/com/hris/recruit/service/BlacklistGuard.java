package com.hris.recruit.service;

import com.hris.common.exception.BizException;
import com.hris.common.exception.ErrorCode;
import com.hris.recruit.client.EmpCheckClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 员工黑名单校验门卫（RCS-F-011）：候选人入库（官网 apply + 管理端手工建档）前统一走此校验。
 *
 * <p>fail-open：emp 服务不可用/超时/返回体异常时 log.warn 放行（MVP 权衡：可用性优先，
 * 黑名单为辅助风控；报告已注明局限）。命中黑名单抛 PARAM_INVALID「候选人命中员工黑名单，禁止入库」——
 * 管理端直显该文案；官网公开链路由调用方 catch 后转通用拒绝文案，不透露内部原因。</p>
 */
@Component
public class BlacklistGuard {

    private static final Logger log = LoggerFactory.getLogger(BlacklistGuard.class);

    /** 命中黑名单的统一文案（管理端语义）。 */
    public static final String HIT_MESSAGE = "候选人命中员工黑名单，禁止入库";

    private final EmpCheckClient empCheckClient;

    public BlacklistGuard(EmpCheckClient empCheckClient) {
        this.empCheckClient = empCheckClient;
    }

    /**
     * 校验候选人是否命中员工黑名单；命中抛 BizException(PARAM_INVALID)。
     *
     * @param certNoHash 证件号哈希（可空，官网投递无证件字段）
     * @param mobile     手机号（内部链路明文透传，与 pre-create 范式一致）
     */
    public void checkOrThrow(String certNoHash, String mobile) {
        if (mobile == null || mobile.isBlank()) {
            return;
        }
        Map<String, Object> data;
        try {
            var resp = empCheckClient.blacklistCheck(certNoHash, mobile);
            if (resp == null || !resp.isSuccess() || resp.getData() == null) {
                // emp 侧业务失败（如端点未上线）按 fail-open 处理
                log.warn("blacklist-check 调用异常，fail-open 放行: code={}, msg={}",
                        resp == null ? "null" : resp.getCode(),
                        resp == null ? "null" : resp.getMessage());
                return;
            }
            data = resp.getData();
        } catch (Exception e) {
            log.warn("blacklist-check 不可用，fail-open 放行: err={}", e.getMessage());
            return;
        }
        if (Boolean.TRUE.equals(data.get("blacklisted"))) {
            log.warn("候选人命中员工黑名单，禁止入库: reason={}", data.get("reason"));
            throw new BizException(ErrorCode.PARAM_INVALID, HIT_MESSAGE);
        }
    }
}
