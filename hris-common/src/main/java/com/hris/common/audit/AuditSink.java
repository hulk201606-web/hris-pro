package com.hris.common.audit;

/**
 * 审计落库 SPI（docs/19 §8.7-2）。
 *
 * <p>{@link AuditLogAspect} 通过 ObjectProvider 注入：容器内有实现则同步落库
 * （失败降级 logback，绝不阻断业务）；无实现保持 logback 输出。</p>
 *
 * <p>实现约定：
 * <ul>
 *   <li>追加式写入：不得更新/删除已有记录；</li>
 *   <li>实现方自行吞掉落库异常或由切面统一降级，禁止把异常抛回业务链路。</li>
 * </ul></p>
 */
@FunctionalInterface
public interface AuditSink {

    /** 追加一条审计记录。实现不得抛出异常阻断业务（切面已兜底，但实现也应自我保护）。 */
    void append(AuditRecord record);
}
