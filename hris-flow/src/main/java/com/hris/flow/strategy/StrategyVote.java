package com.hris.flow.strategy;

/**
 * 单次投票后的节点计票快照（FLW-F-008）。
 *
 * @param threshold      比例阈值（百分比 1-100，仅 PERCENT 使用）
 * @param approvedCount  含本次在内的累计同意数
 * @param totalCount     节点总票数（节点首次评估时按当时的待办任务数锁定）
 * @param remainingCount 本次投出后仍待处理的任务数
 * @param vetoed         是否已出现否决
 */
public record StrategyVote(int threshold, int approvedCount, int totalCount,
                           int remainingCount, boolean vetoed) {

    /** 通过所需最少同意数：向上取整，避免 2/3 场景被 66% 截断。 */
    public int requiredCount() {
        if (totalCount <= 0) {
            return 1;
        }
        int pct = Math.max(1, Math.min(100, threshold));
        return (int) Math.ceil(totalCount * pct / 100.0d);
    }

    /** 当前同意率（百分比，向下取整），仅用于留痕与日志。 */
    public int approvedPercent() {
        return totalCount <= 0 ? 0 : (int) (approvedCount * 100L / totalCount);
    }
}
