package com.hris.flow.strategy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 一票否决策略单测（FLW-F-008）：任一否决即驳回，无需等其余票。
 */
class VetoStrategyTest {

    private final VetoStrategy strategy = new VetoStrategy();

    @Test
    @DisplayName("任一否决即驳回（其余票仍在）")
    void shouldRejectOnAnyVeto() {
        StrategyVote vote = new StrategyVote(60, 2, 5, 2, true);

        assertEquals(StrategyDecision.REJECT, strategy.decide(vote));
    }

    @Test
    @DisplayName("无否决且仍有票未投 → 等待")
    void shouldWaitWhenNoVetoAndVotesRemain() {
        assertEquals(StrategyDecision.WAIT, strategy.decide(new StrategyVote(60, 1, 3, 2, false)));
    }

    @Test
    @DisplayName("全部同意投完 → 节点通过")
    void shouldPassWhenAllApproved() {
        assertEquals(StrategyDecision.PASS, strategy.decide(new StrategyVote(60, 3, 3, 0, false)));
    }
}
