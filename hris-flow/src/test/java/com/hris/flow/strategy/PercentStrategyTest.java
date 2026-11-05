package com.hris.flow.strategy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 比例通过策略单测（FLW-F-008）：达标提前通过 / 未达标等待 / 末票未达标驳回。
 */
class PercentStrategyTest {

    private final PercentStrategy strategy = new PercentStrategy();

    @Test
    @DisplayName("3 人会签 60%：2 票同意（剩余 1 票未投）→ 提前通过")
    void shouldPassWhenThresholdReached() {
        StrategyVote vote = new StrategyVote(60, 2, 3, 1, false);

        assertEquals(2, vote.requiredCount(), "3 票的 60% 向上取整为 2");
        assertEquals(StrategyDecision.PASS, strategy.decide(vote));
    }

    @Test
    @DisplayName("3 人会签 60%：1 票同意（剩余 2 票）→ 等待，不提前通过")
    void shouldWaitWhenThresholdNotReached() {
        StrategyVote vote = new StrategyVote(60, 1, 3, 2, false);

        assertEquals(StrategyDecision.WAIT, strategy.decide(vote));
    }

    @Test
    @DisplayName("末票同意但比例仍不足（1/2 &lt; 60%）→ 节点驳回")
    void shouldRejectWhenLastVoteStillBelowThreshold() {
        StrategyVote vote = new StrategyVote(60, 1, 2, 0, false);

        assertEquals(StrategyDecision.REJECT, strategy.decide(vote));
    }

    @Test
    @DisplayName("剩余票全同意也不足阈值 → 立即驳回，不必等剩余票")
    void shouldRejectEarlyWhenThresholdUnreachable() {
        StrategyVote vote = new StrategyVote(90, 1, 5, 1, false);

        assertEquals(StrategyDecision.REJECT, strategy.decide(vote));
    }

    @Test
    @DisplayName("全票通过（含 100% 阈值）→ 通过")
    void shouldPassOnUnanimous() {
        assertEquals(StrategyDecision.PASS, strategy.decide(new StrategyVote(100, 4, 4, 0, false)));
    }
}
