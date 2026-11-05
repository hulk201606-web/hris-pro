package com.hris.flow.strategy;

import org.springframework.stereotype.Component;

/**
 * 比例通过策略（FLW-F-008）：多人会签节点，同意数 / 总票数 &gt;= 阈值即节点通过。
 *
 * <p>判定顺序：</p>
 * <ol>
 *   <li>同意数已达 requiredCount → {@link StrategyDecision#PASS}（提前完成，剩余会签任务由
 *       动作服务关停）；</li>
 *   <li>剩余票全同意也不足阈值，或已无剩余票 → {@link StrategyDecision#REJECT}；</li>
 *   <li>否则 {@link StrategyDecision#WAIT}（本票正常完成，节点继续等待）。</li>
 * </ol>
 */
@Component
public class PercentStrategy implements ApprovalStrategy {

    @Override
    public ApprovalStrategyType type() {
        return ApprovalStrategyType.PERCENT;
    }

    @Override
    public StrategyDecision decide(StrategyVote vote) {
        int required = vote.requiredCount();
        if (vote.approvedCount() >= required) {
            return StrategyDecision.PASS;
        }
        int bestPossible = vote.approvedCount() + Math.max(0, vote.remainingCount());
        if (vote.remainingCount() <= 0 || bestPossible < required) {
            return StrategyDecision.REJECT;
        }
        return StrategyDecision.WAIT;
    }
}
