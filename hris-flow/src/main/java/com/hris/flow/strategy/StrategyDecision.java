package com.hris.flow.strategy;

/**
 * 节点级策略评估结论（FLW-F-008）。
 *
 * <p>由策略实现返回，任务动作服务据此驱动引擎：</p>
 * <ul>
 *   <li>{@link #PASS}：节点已达成通过条件——提前完成任务并关停同节点剩余会签任务；</li>
 *   <li>{@link #WAIT}：条件未达成且仍有票未投——本票正常完成，节点继续等待；</li>
 *   <li>{@link #REJECT}：条件已不可能达成（如剩余票全同意也不足阈值、或触发一票否决）——
 *       节点驳回。</li>
 * </ul>
 */
public enum StrategyDecision {

    PASS,
    WAIT,
    REJECT
}
