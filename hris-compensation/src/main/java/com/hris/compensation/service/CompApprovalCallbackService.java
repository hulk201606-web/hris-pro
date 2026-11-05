package com.hris.compensation.service;

import com.hris.compensation.dto.ApprovalDtos.ApprovalCallbackResponse;
import com.hris.compensation.entity.PayrollRun;
import com.hris.compensation.mapper.PayrollRunMapper;
import com.hris.common.exception.BizException;
import com.hris.common.exception.ErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 薪酬域审批回调（flow → cmp，docs/19 §9 遗留项四域接入）。
 *
 * <p>幂等状态迁移铁律：先读当前状态，仅复核中状态可迁移，终态/不可迁移状态返回 changed=false，
 * 重复回调不产生任何副作用。</p>
 *
 * <p>状态迁移语义（与 docs/db/schema-mysql.sql 枚举对齐）：
 * <ul>
 *   <li>CMP_PAYROLL → cmp_payroll_run.status（INIT/TRIAL/CALC/REVIEW/APPROVE/PAID/LOCK）：
 *       REVIEW 时 approve→APPROVE（复核通过，须双人复核已完备，见 CMP-F-013b）；
 *       PAID/LOCK/APPROVE 为终态 changed=false；
 *       reject 无载体（枚举无 REJECTED，驳回即维持 REVIEW 待修正重提）changed=false。</li>
 *   <li>CMP_ADJUST → cmp_payroll_adjustment 无审批状态/流程字段，回调无迁移载体，
 *       返回 changed=false（见报告遗留说明）。</li>
 * </ul></p>
 *
 * <p>bizKey 约定：cmp 域当前无审批 Feign 发起点（接入方后续对齐），约定 bizKey = 算薪批次主键 id
 * 十进制字符串；若后续发起点改用其它键，须同步修改 {@link #handleCallback} 的定位逻辑。</p>
 */
@Service
public class CompApprovalCallbackService {

    private static final Logger log = LoggerFactory.getLogger(CompApprovalCallbackService.class);

    private final PayrollRunMapper payrollRunMapper;

    public CompApprovalCallbackService(PayrollRunMapper payrollRunMapper) {
        this.payrollRunMapper = payrollRunMapper;
    }

    /**
     * 审批结果回调入口（bizType：CMP_PAYROLL / CMP_ADJUST）。
     * 未知 bizType 不报错（返回 changed=false），保持审批动作永不失败的契约。
     */
    @Transactional(rollbackFor = Exception.class)
    public ApprovalCallbackResponse handleCallback(String bizType, String bizKey, boolean approved) {
        return switch (bizType == null ? "" : bizType) {
            case "CMP_PAYROLL" -> handlePayroll(bizKey, approved);
            case "CMP_ADJUST" -> {
                log.info("CMP_ADJUST 无审批载体（cmp_payroll_adjustment 无审批字段），回调忽略: bizKey={}", bizKey);
                yield new ApprovalCallbackResponse(false, null);
            }
            default -> {
                log.warn("cmp 域未知审批 bizType，忽略: bizType={}, bizKey={}", bizType, bizKey);
                yield new ApprovalCallbackResponse(false, null);
            }
        };
    }

    /**
     * 算薪批次审批：REVIEW → approve:APPROVE，但须先完成双人复核（CMP-F-013b）。
     *
     * <p>单步审批通道已收口：回调只做「流程侧放行」，业务侧仍要求 reviewed_by_1/2 两名不同
     * 用户已复核；未满足则 changed=false（批次保持 REVIEW，等 /review 端点补齐复核人）。
     * INIT/TRIAL/CALC 未达复核阶段，APPROVE/PAID/LOCK 已过审批环节，均 changed=false。</p>
     */
    private ApprovalCallbackResponse handlePayroll(String bizKey, boolean approved) {
        PayrollRun e = payrollRunMapper.selectById(parseBizKey(bizKey));
        if (e == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "算薪批次不存在");
        }
        if (!"REVIEW".equals(e.getStatus())) {
            return new ApprovalCallbackResponse(false, e.getStatus());
        }
        if (!approved) {
            // 驳回无载体：枚举无 REJECTED，维持 REVIEW 待修正重提
            return new ApprovalCallbackResponse(false, e.getStatus());
        }
        if (!PayrollReviewService.dualReviewCompleted(e)) {
            log.info("流程侧审批通过但双人复核未完成，暂不流转 APPROVE: bizKey={}", bizKey);
            return new ApprovalCallbackResponse(false, e.getStatus());
        }
        e.setStatus("APPROVE");
        payrollRunMapper.updateById(e);
        return new ApprovalCallbackResponse(true, e.getStatus());
    }

    /** bizKey 解析（约定 = 主键 id 十进制字符串），非法格式抛 PARAM_INVALID 保持与 flow 重试契约。 */
    private Long parseBizKey(String bizKey) {
        try {
            return Long.parseLong(bizKey);
        } catch (NumberFormatException ex) {
            throw new BizException(ErrorCode.PARAM_INVALID, "bizKey 非法（约定为算薪批次 id）: " + bizKey);
        }
    }
}
