package com.hris.recruit.service;

import com.hris.common.exception.BizException;
import com.hris.common.exception.ErrorCode;
import com.hris.recruit.client.EmpCheckClient;
import com.hris.recruit.config.RecruitProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 员工黑名单校验门卫（RCS-F-011 / 批 7 #14）：候选人入库（官网 apply + 管理端手工建档）
 * 前统一走此校验。
 *
 * <p>降级模式由配置 {@code hris.recruit.blacklist.fail-open} 控制（缺省 true 保持现状）：
 * <ul>
 *   <li>fail-open=true：emp 服务不可用/超时/返回体异常时 log.warn 放行（可用性优先，
 *       黑名单为辅助风控）；</li>
 *   <li>fail-open=false：同样场景直接拒绝（PARAM_INVALID「系统繁忙请稍后再试」，
 *       不透露内部原因），官网投递与管理端建候选均不可绕过黑名单校验。</li>
 * </ul>
 * 命中黑名单恒抛 PARAM_INVALID「候选人命中员工黑名单，禁止入库」——管理端直显该文案；
 * 官网公开链路由调用方 catch 后转通用拒绝文案，不透露内部原因。</p>
 */
@Component
public class BlacklistGuard {

    private static final Logger log = LoggerFactory.getLogger(BlacklistGuard.class);

    /** 命中黑名单的统一文案（管理端语义）。 */
    public static final String HIT_MESSAGE = "候选人命中员工黑名单，禁止入库";

    /** fail-closed 时 emp 不可用的统一拒绝文案（不透露内部原因）。 */
    public static final String BUSY_MESSAGE = "系统繁忙请稍后再试";

    private final EmpCheckClient empCheckClient;
    private final boolean failOpen;

    public BlacklistGuard(EmpCheckClient empCheckClient, RecruitProperties properties) {
        this.empCheckClient = empCheckClient;
        this.failOpen = properties.getBlacklist().isFailOpen();
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
                // emp 侧业务失败（如端点未上线）按降级模式处理
                handleUnavailable("业务失败: code=" + (resp == null ? "null" : resp.getCode())
                        + ", msg=" + (resp == null ? "null" : resp.getMessage()));
                return;
            }
            data = resp.getData();
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            handleUnavailable("不可用/超时: err=" + e.getMessage());
            return;
        }
        if (Boolean.TRUE.equals(data.get("blacklisted"))) {
            log.warn("候选人命中员工黑名单，禁止入库: reason={}", data.get("reason"));
            throw new BizException(ErrorCode.PARAM_INVALID, HIT_MESSAGE);
        }
    }

    /** emp 校验不可用时的降级分支：fail-open 放行告警；fail-closed 拒绝（通用文案）。 */
    private void handleUnavailable(String reason) {
        if (failOpen) {
            log.warn("blacklist-check 异常，fail-open 放行: {}", reason);
            return;
        }
        log.warn("blacklist-check 异常，fail-closed 拒绝: {}", reason);
        throw new BizException(ErrorCode.PARAM_INVALID, BUSY_MESSAGE);
    }
}
