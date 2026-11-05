package com.hris.compensation.service;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.hris.common.exception.BizException;
import com.hris.common.exception.ErrorCode;
import com.hris.common.security.AuthContextHolder;
import com.hris.compensation.dto.PayrollRunDtos.PayrollReviewRequest;
import com.hris.compensation.dto.PayrollRunDtos.PayrollRunResponse;
import com.hris.compensation.entity.PayrollRun;
import com.hris.compensation.mapper.PayrollRunMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * 算薪双人复核（CMP-F-013b）。
 *
 * <p>语义：REVIEW 状态的批次需两名<b>不同</b>用户各复核一次，两条复核记录齐全后
 * <b>自动</b>流转 APPROVE（无需再点一次审批；这是本实现的明确取舍，替代原单步 approve）。
 * 同一用户重复复核 → PARAM_INVALID「需他人复核」；计算人不得复核（双人原则：计算者≠复核者）。</p>
 *
 * <p>驳回（approved=false）：退回 CALC 待修正重算，清空两名复核人（重新走完整双人流程），
 * 驳回理由写入 review_comment 留痕；不发流程回调（flow 侧由调用方按 CMP_PAYROLL 重提）。</p>
 *
 * <p>为什么用两个标量列而非复核子表：双人上限固定为 2，标量列读写简单且复用现有
 * BaseEntity 乐观锁；若未来需 N 人会签，再演进为 cmp_payroll_review 子表。</p>
 */
@Service
public class PayrollReviewService {

    private static final Logger log = LoggerFactory.getLogger(PayrollReviewService.class);

    /** 状态：待复核 */
    public static final String STATUS_REVIEW = "REVIEW";
    /** 状态：复核通过 */
    public static final String STATUS_APPROVE = "APPROVE";
    /** 状态：计算完成（驳回后退回该状态修正重算） */
    public static final String STATUS_CALC = "CALC";

    /** 意见长度上限（与库列 varchar(255) 对齐）。 */
    private static final int COMMENT_MAX = 255;

    private final PayrollRunMapper mapper;

    public PayrollReviewService(PayrollRunMapper mapper) {
        this.mapper = mapper;
    }

    /**
     * 记录一次复核并推进状态机。
     *
     * @param id   批次主键
     * @param req  approved + comment
     */
    @Transactional(rollbackFor = Exception.class)
    public PayrollRunResponse review(Long id, PayrollReviewRequest req) {
        PayrollRun run = mapper.selectById(id);
        if (run == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "PayrollRun 不存在");
        }
        if (!STATUS_REVIEW.equals(run.getStatus())) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "当前状态不可复核（" + run.getStatus() + "），仅 REVIEW 可复核");
        }
        Long userId = AuthContextHolder.getUserId();
        if (userId == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "无法识别复核人（缺少登录上下文）");
        }
        String comment = truncate(req.comment());

        if (!Boolean.TRUE.equals(req.approved())) {
            reject(run, userId, comment);
            return toResponse(run);
        }
        // 计算人不得复核（职责分离），同一人不得占用两个复核位
        if (userId.equals(run.getCalcBy())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "复核人不可为计算人（双人原则）");
        }
        if (userId.equals(run.getReviewedBy1()) || userId.equals(run.getReviewedBy2())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "需他人复核：同一用户不可重复复核");
        }
        if (run.getReviewedBy1() == null) {
            run.setReviewedBy1(userId);
            run.setReviewedAt1(LocalDateTime.now());
        } else if (run.getReviewedBy2() == null) {
            run.setReviewedBy2(userId);
            run.setReviewedAt2(LocalDateTime.now());
        } else {
            throw new BizException(ErrorCode.PARAM_INVALID, "双人复核已完成，无需再次复核");
        }
        if (comment != null) {
            run.setReviewComment(comment);
        }
        // 两人均通过（且互不相同）→ 自动流转 APPROVE
        if (dualReviewCompleted(run)) {
            run.setStatus(STATUS_APPROVE);
            run.setReviewBy(run.getReviewedBy2());
            log.info("双人复核完成，批次自动流转 APPROVE: runId={}, reviewers={}/{}",
                    id, run.getReviewedBy1(), run.getReviewedBy2());
        }
        if (mapper.updateById(run) == 0) {
            throw new BizException(ErrorCode.CMP_ERROR, "数据已被其他操作变更，请刷新后重试");
        }
        return toResponse(run);
    }

    /** 驳回：退回 CALC，清空双方复核位与最终复核人，记录理由。 */
    private void reject(PayrollRun run, Long userId, String comment) {
        String reason = comment == null ? "复核驳回" : comment;
        // 复核位需显式置 NULL：updateById 默认忽略 null 字段，故走 UpdateWrapper
        int updated = mapper.update(null, new LambdaUpdateWrapper<PayrollRun>()
                .eq(PayrollRun::getId, run.getId())
                .eq(PayrollRun::getStatus, STATUS_REVIEW)
                .set(PayrollRun::getStatus, STATUS_CALC)
                .set(PayrollRun::getReviewedBy1, null)
                .set(PayrollRun::getReviewedAt1, null)
                .set(PayrollRun::getReviewedBy2, null)
                .set(PayrollRun::getReviewedAt2, null)
                .set(PayrollRun::getReviewBy, null)
                .set(PayrollRun::getReviewComment, reason)
                .set(PayrollRun::getUpdatedAt, LocalDateTime.now()));
        if (updated == 0) {
            throw new BizException(ErrorCode.CMP_ERROR, "数据已被其他操作变更，请刷新后重试");
        }
        run.setStatus(STATUS_CALC);
        run.setReviewedBy1(null);
        run.setReviewedAt1(null);
        run.setReviewedBy2(null);
        run.setReviewedAt2(null);
        run.setReviewBy(null);
        run.setReviewComment(reason);
        log.info("复核驳回，批次退回 CALC: runId={}, reviewer={}", run.getId(), userId);
    }

    /** 双人复核是否完备（两名不同用户）。 */
    public static boolean dualReviewCompleted(PayrollRun run) {
        return run.getReviewedBy1() != null && run.getReviewedBy2() != null
                && !Objects.equals(run.getReviewedBy1(), run.getReviewedBy2());
    }

    private String truncate(String comment) {
        if (comment == null || comment.isBlank()) {
            return null;
        }
        String c = comment.trim();
        return c.length() > COMMENT_MAX ? c.substring(0, COMMENT_MAX) : c;
    }

    private PayrollRunResponse toResponse(PayrollRun e) {
        return new PayrollRunResponse(e.getId(), e.getPeriodCode(), e.getPayGroupId(), e.getStatus(),
                e.getCalcBy(), e.getReviewBy(), e.getCreatedAt(), e.getUpdatedAt(),
                e.getReviewedBy1(), e.getReviewedAt1(), e.getReviewedBy2(), e.getReviewedAt2(),
                e.getReviewComment());
    }
}
