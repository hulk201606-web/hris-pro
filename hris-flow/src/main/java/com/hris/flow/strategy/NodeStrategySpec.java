package com.hris.flow.strategy;

/**
 * 节点策略配置（FLW-F-008），由 BPMN 用户任务 extensionElements 解析而来。
 *
 * <p>支持的 XML 形态（两种等价，均不改现有 17 个模板）：</p>
 * <pre>
 * &lt;userTask id="committee" name="薪酬委员会审批"&gt;
 *   &lt;extensionElements&gt;
 *     &lt;hris:approvalStrategy strategy="PERCENT" percentThreshold="60"/&gt;
 *     &lt;!-- 或 &lt;flowable:property name="strategy" value="PERCENT"/&gt;
 *          &lt;flowable:property name="percentThreshold" value="60"/&gt; --&gt;
 *   &lt;/extensionElements&gt;
 * &lt;/userTask&gt;
 * </pre>
 *
 * @param type             策略类型
 * @param percentThreshold 比例阈值（百分比），非 PERCENT 策略忽略
 */
public record NodeStrategySpec(ApprovalStrategyType type, int percentThreshold) {

    /** 缺省比例阈值（PLT 未配置时按 60% 处理）。 */
    public static final int DEFAULT_PERCENT_THRESHOLD = 60;

    public NodeStrategySpec {
        percentThreshold = Math.max(1, Math.min(100, percentThreshold));
    }

    public static NodeStrategySpec of(ApprovalStrategyType type, Integer threshold) {
        int t = threshold == null ? DEFAULT_PERCENT_THRESHOLD : threshold;
        return new NodeStrategySpec(type, t);
    }
}
