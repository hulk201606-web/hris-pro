package com.hris.performance.service;

import com.hris.common.exception.BizException;
import com.hris.common.exception.ErrorCode;

/**
 * 周期状态守卫（PRF-F-002）。所有绩效写路径（周期配置/方案/目标/评价任务/评分/申诉）
 * 统一经此判定，避免各 Service 各自散落 if 判断导致口径漂移。
 *
 * <p>错误码约定：非法流转 → PARAM_INVALID（调用方参数问题）；状态不允许写 → PRF_ERROR
 * （业务状态冲突，前端据此提示"周期已关闭/已进入评分阶段"）。</p>
 */
public final class CycleStateMachine {

    /** 状态机白名单提示（错误消息统一引用，避免各端点文案不一致）。 */
    public static final String TRANSITION_HINT = "DRAFT→ACTIVE→SCORING→CLOSED";

    private CycleStateMachine() {
    }

    /**
     * 校验状态流转合法性。target 必须是四态之一，且落在白名单边上。
     *
     * @param current 当前状态（兼容历史值）
     * @param target  目标状态
     * @throws BizException PARAM_INVALID：目标非法或不在白名单
     */
    public static CycleStatus requireTransition(String current, String target) {
        CycleStatus from = CycleStatus.resolve(current);
        CycleStatus to = CycleStatus.of(target);
        if (to == null) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "目标状态非法: " + target + "（可选 DRAFT/ACTIVE/SCORING/CLOSED）");
        }
        if (!from.canTransitionTo(to)) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "不允许的状态流转: " + from + " → " + to + "（白名单 " + TRANSITION_HINT + "）");
        }
        return to;
    }

    /** 周期配置（周期自身字段/方案模板）仅 DRAFT 可改。 */
    public static void requireConfigEditable(String status) {
        CycleStatus s = CycleStatus.resolve(status);
        if (s.locked()) {
            throw new BizException(ErrorCode.PRF_ERROR, "考核周期已关闭，禁止再修改");
        }
        if (!s.configEditable()) {
            throw new BizException(ErrorCode.PRF_ERROR,
                    "周期当前为 " + s + "，仅 DRAFT 可编辑周期配置");
        }
    }

    /** 目标/OKR：DRAFT、ACTIVE 可写；SCORING 起冻结（规格：评分开始后不可增删目标）。 */
    public static void requireGoalWritable(String status) {
        CycleStatus s = CycleStatus.resolve(status);
        if (s.locked()) {
            throw new BizException(ErrorCode.PRF_ERROR, "考核周期已关闭，禁止增删改目标");
        }
        if (s == CycleStatus.SCORING) {
            throw new BizException(ErrorCode.PRF_ERROR, "考核周期已进入评分阶段（SCORING），禁止增删改目标");
        }
    }

    /** 评价任务树：DRAFT、ACTIVE 可写；SCORING/CLOSED 冻结。 */
    public static void requireTaskWritable(String status) {
        CycleStatus s = CycleStatus.resolve(status);
        if (s.locked()) {
            throw new BizException(ErrorCode.PRF_ERROR, "考核周期已关闭，禁止变更评价任务");
        }
        if (s == CycleStatus.SCORING) {
            throw new BizException(ErrorCode.PRF_ERROR, "考核周期已进入评分阶段，禁止增删评价任务");
        }
    }

    /**
     * 评分写入：自评（SELF）只在 ACTIVE 自评窗口开放；上级/互评/下级在 ACTIVE、SCORING 开放；
     * DRAFT（未启动）与 CLOSED（已封板）一律拒绝。
     */
    public static void requireScoringWritable(String status, String evaluatorRole) {
        CycleStatus s = CycleStatus.resolve(status);
        boolean self = "SELF".equalsIgnoreCase(evaluatorRole == null ? "" : evaluatorRole.trim());
        if (s.locked()) {
            throw new BizException(ErrorCode.PRF_ERROR, "考核周期已关闭，禁止提交或变更评分");
        }
        if (s == CycleStatus.DRAFT) {
            throw new BizException(ErrorCode.PRF_ERROR, "考核周期尚未启动（DRAFT），禁止发起评分");
        }
        if (self && s != CycleStatus.ACTIVE) {
            throw new BizException(ErrorCode.PRF_ERROR, "自评窗口已关闭（周期 " + s + "），禁止变更自评分");
        }
    }

    /** 申诉：结果公布（CLOSED）后才可发起，是封板期唯一允许的写入口（PRF-F-009）。 */
    public static void requireAppealWritable(String status) {
        CycleStatus s = CycleStatus.resolve(status);
        if (s != CycleStatus.CLOSED) {
            throw new BizException(ErrorCode.PRF_ERROR,
                    "仅考核周期关闭（结果公布）后可发起申诉，当前为 " + s);
        }
    }

    /** 方案模板：被任何已启动周期（非 DRAFT）引用后不可改，防止改规则影响在途考核。 */
    public static void requirePlanWritable(boolean referencedByStartedCycle) {
        if (referencedByStartedCycle) {
            throw new BizException(ErrorCode.PRF_ERROR,
                    "方案已被非 DRAFT 周期引用，禁止修改或删除");
        }
    }
}
