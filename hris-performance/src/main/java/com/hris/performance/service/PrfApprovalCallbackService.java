package com.hris.performance.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hris.performance.dto.ApprovalDtos.ApprovalCallbackResponse;
import com.hris.performance.entity.Appeal;
import com.hris.performance.mapper.AppealMapper;
import com.hris.common.exception.BizException;
import com.hris.common.exception.ErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 绩效域审批回调（flow → prf，docs/19 §9 遗留项四域接入）。
 *
 * <p>幂等状态迁移铁律：先读当前状态，仅处理中状态可迁移，终态/不可迁移状态返回 changed=false，
 * 重复回调不产生任何副作用。</p>
 *
 * <p>状态迁移语义（与 docs/db/schema-mysql.sql 枚举对齐）：
 * <ul>
 *   <li>PRF_APPEAL → prf_appeal.status（DRAFT/PROCESSING/APPROVED/REJECTED）：
 *       PROCESSING 时 approve→APPROVED、reject→REJECTED（审批结论回写 status）；
 *       DRAFT 未提交流程、APPROVED/REJECTED 终态，均 changed=false。</li>
 *   <li>PRF_TRAINPLAN → prf_train_plan（name/code/startDate/endDate）无 status/biz_key/
 *       proc_inst_id 等审批载体字段，回调无迁移落点，返回 changed=false（见报告遗留说明）。</li>
 * </ul></p>
 *
 * <p>bizKey 约定：prf_appeal 表自带唯一键 uk_prf_appeal_biz（tenant_id+biz_key，注释"流程幂等键"），
 * bizKey = appeal.biz_key 原值。域内当前无审批 Feign 发起点，后续接入者发起时须把 biz_key
 * 原样作为流程 bizKey 传入，保证回调能按唯一键定位。</p>
 */
@Service
public class PrfApprovalCallbackService {

    private static final Logger log = LoggerFactory.getLogger(PrfApprovalCallbackService.class);

    private final AppealMapper appealMapper;

    public PrfApprovalCallbackService(AppealMapper appealMapper) {
        this.appealMapper = appealMapper;
    }

    /**
     * 审批结果回调入口（bizType：PRF_APPEAL / PRF_TRAINPLAN）。
     * 未知 bizType 不报错（返回 changed=false），保持审批动作永不失败的契约。
     */
    @Transactional(rollbackFor = Exception.class)
    public ApprovalCallbackResponse handleCallback(String bizType, String bizKey, boolean approved) {
        return switch (bizType == null ? "" : bizType) {
            case "PRF_APPEAL" -> handleAppeal(bizKey, approved);
            case "PRF_TRAINPLAN" -> {
                log.info("PRF_TRAINPLAN 无审批载体（prf_train_plan 无审批字段），回调忽略: bizKey={}", bizKey);
                yield new ApprovalCallbackResponse(false, null);
            }
            default -> {
                log.warn("prf 域未知审批 bizType，忽略: bizType={}, bizKey={}", bizType, bizKey);
                yield new ApprovalCallbackResponse(false, null);
            }
        };
    }

    /**
     * 绩效申诉审批：PROCESSING → approve:APPROVED / reject:REJECTED（按 biz_key 唯一键定位）。
     * DRAFT 与终态均 changed=false。
     */
    private ApprovalCallbackResponse handleAppeal(String bizKey, boolean approved) {
        Appeal e = appealMapper.selectOne(new LambdaQueryWrapper<Appeal>()
                .eq(Appeal::getBizKey, bizKey)
                .last("LIMIT 1"));
        if (e == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "绩效申诉单不存在");
        }
        if (!"PROCESSING".equals(e.getStatus())) {
            return new ApprovalCallbackResponse(false, e.getStatus());
        }
        e.setStatus(approved ? "APPROVED" : "REJECTED");
        appealMapper.updateById(e);
        return new ApprovalCallbackResponse(true, e.getStatus());
    }
}
