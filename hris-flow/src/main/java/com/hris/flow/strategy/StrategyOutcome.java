package com.hris.flow.strategy;

/**
 * 节点策略评估结果（FLW-F-008）：结论 + 计票快照，供动作服务决定引擎操作与留痕文案。
 */
public record StrategyOutcome(StrategyDecision decision, NodeStrategySpec spec,
                              StrategyVote vote, String message) {

    /** 是否本票后节点已达成通过（可提前完成）。 */
    public boolean passed() {
        return decision == StrategyDecision.PASS;
    }

    /** 是否应走驳回语义。 */
    public boolean rejected() {
        return decision == StrategyDecision.REJECT;
    }

    public static StrategyOutcome of(StrategyDecision decision, NodeStrategySpec spec, StrategyVote vote) {
        return new StrategyOutcome(decision, spec, vote, describe(decision, spec, vote));
    }

    private static String describe(StrategyDecision decision, NodeStrategySpec spec, StrategyVote vote) {
        String base = spec.type() == ApprovalStrategyType.PERCENT
                ? "比例通过 " + vote.approvedCount() + "/" + vote.totalCount()
                        + "（阈值 " + vote.requiredCount() + "，" + spec.percentThreshold() + "%）"
                : "一票否决（同意 " + vote.approvedCount() + "/" + vote.totalCount() + "）";
        return switch (decision) {
            case PASS -> base + "，节点通过";
            case WAIT -> base + "，等待其余会签人";
            case REJECT -> base + "，节点驳回";
        };
    }
}
