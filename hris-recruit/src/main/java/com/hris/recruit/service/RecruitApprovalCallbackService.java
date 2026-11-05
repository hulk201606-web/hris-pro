package com.hris.recruit.service;

import com.hris.recruit.dto.ApprovalDtos.ApprovalCallbackResponse;
import com.hris.recruit.entity.Offer;
import com.hris.recruit.entity.Requisition;
import com.hris.recruit.mapper.OfferMapper;
import com.hris.recruit.mapper.RequisitionMapper;
import com.hris.common.exception.BizException;
import com.hris.common.exception.ErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 招聘域审批回调（flow → rcs，docs/19 §9 遗留项四域接入）。
 *
 * <p>幂等状态迁移铁律：先读当前状态，仅审批中状态可迁移，终态/不可迁移状态返回 changed=false，
 * 重复回调不产生任何副作用。</p>
 *
 * <p>状态迁移语义（与 docs/db/schema-mysql.sql 枚举对齐）：
 * <ul>
 *   <li>RCS_REQUISITION → rcu_requisition.status（DRAFT/APPROVING/OPEN/CLOSED）：
 *       APPROVING 时 approve→OPEN（审批通过职位开放）、reject→CLOSED；
 *       其余状态 changed=false。</li>
 *   <li>RCS_OFFER → rcu_offer.status（DRAFT/SENT/ACCEPTED/REJECTED）为候选人交互状态，
 *       不含审批结论载体（无「已批准待发送」枚举值），故回调仅返回当前状态 changed=false，
 *       审批语义暂无落点（见报告遗留说明）。</li>
 * </ul></p>
 *
 * <p>bizKey 约定：rcs 域当前无审批 Feign 发起点（接入方后续对齐），约定 bizKey = 单据主键 id 十进制字符串；
 * 若后续发起点改用业务单号（req_no/offer_no），须同步修改 {@link #handleCallback} 的定位逻辑。</p>
 */
@Service
public class RecruitApprovalCallbackService {

    private static final Logger log = LoggerFactory.getLogger(RecruitApprovalCallbackService.class);

    private final RequisitionMapper requisitionMapper;
    private final OfferMapper offerMapper;

    public RecruitApprovalCallbackService(RequisitionMapper requisitionMapper, OfferMapper offerMapper) {
        this.requisitionMapper = requisitionMapper;
        this.offerMapper = offerMapper;
    }

    /**
     * 审批结果回调入口（bizType：RCS_REQUISITION / RCS_OFFER）。
     * 未知 bizType 不报错（返回 changed=false），保持审批动作永不失败的契约。
     */
    @Transactional(rollbackFor = Exception.class)
    public ApprovalCallbackResponse handleCallback(String bizType, String bizKey, boolean approved) {
        return switch (bizType == null ? "" : bizType) {
            case "RCS_REQUISITION" -> handleRequisition(bizKey, approved);
            case "RCS_OFFER" -> handleOffer(bizKey);
            default -> {
                log.warn("rcs 域未知审批 bizType，忽略: bizType={}, bizKey={}", bizType, bizKey);
                yield new ApprovalCallbackResponse(false, null);
            }
        };
    }

    /**
     * 招聘需求审批：APPROVING → approve:OPEN / reject:CLOSED。
     * DRAFT（流程未提交）与 OPEN/CLOSED（终态）均 changed=false。
     */
    private ApprovalCallbackResponse handleRequisition(String bizKey, boolean approved) {
        Requisition e = requisitionMapper.selectById(parseBizKey(bizKey));
        if (e == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "招聘需求不存在");
        }
        if (!"APPROVING".equals(e.getStatus())) {
            return new ApprovalCallbackResponse(false, e.getStatus());
        }
        e.setStatus(approved ? "OPEN" : "CLOSED");
        requisitionMapper.updateById(e);
        return new ApprovalCallbackResponse(true, e.getStatus());
    }

    /**
     * Offer 审批回调：rcu_offer.status 枚举（DRAFT/SENT/ACCEPTED/REJECTED）为候选人交互状态，
     * 无审批结论载体——不迁移，返回当前状态（幂等成功）。
     */
    private ApprovalCallbackResponse handleOffer(String bizKey) {
        Offer e = offerMapper.selectById(parseBizKey(bizKey));
        if (e == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "Offer 不存在");
        }
        return new ApprovalCallbackResponse(false, e.getStatus());
    }

    /** bizKey 解析（约定 = 主键 id 十进制字符串），非法格式抛 PARAM_INVALID 保持与 flow 重试契约。 */
    private Long parseBizKey(String bizKey) {
        try {
            return Long.parseLong(bizKey);
        } catch (NumberFormatException ex) {
            throw new BizException(ErrorCode.PARAM_INVALID, "bizKey 非法（约定为单据 id）: " + bizKey);
        }
    }
}
