package com.hris.flow.strategy;

import org.springframework.stereotype.Component;

/**
 * 一票否决策略（FLW-F-008）：任一否决即整体驳回，无需等待其余票。
 *
 * <p>否决票之后剩余会签任务由动作服务按驳回语义处置（终止或退回发起人）；
 * 全部投完且无否决时返回 {@link StrategyDecision#PASS} 以统一驱动节点通过。</p>
 */
@Component
public class VetoStrategy implements ApprovalStrategy {

    @Override
    public ApprovalStrategyType type() {
        return ApprovalStrategyType.VETO;
    }

    @Override
    public StrategyDecision decide(StrategyVote vote) {
        if (vote.vetoed()) {
            return StrategyDecision.REJECT;
        }
        if (vote.remainingCount() <= 0) {
            return StrategyDecision.PASS;
        }
        return StrategyDecision.WAIT;
    }
}
