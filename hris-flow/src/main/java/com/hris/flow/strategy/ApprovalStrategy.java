package com.hris.flow.strategy;

/**
 * 会签策略抽象（FLW-F-008）：只做纯计算，不接触引擎。
 *
 * <p>实现类必须无状态、线程安全；引擎交互（读写流程变量、关停剩余任务）由
 * {@code TaskStrategyService} 承担，保证策略可独立单测。</p>
 */
public interface ApprovalStrategy {

    /** 策略类型（工厂注册键）。 */
    ApprovalStrategyType type();

    /** 依据计票快照给出节点级结论。 */
    StrategyDecision decide(StrategyVote vote);
}
