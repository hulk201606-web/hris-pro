package com.hris.common.audit;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 审计埋点注解。标记需要审计的关键操作（登录、权限变更、审批动作、敏感查看/导出等，
 * 见 docs/08 §5）。切面见 {@link AuditLogAspect}。
 */
@Documented
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface AuditLog {

    /** 操作描述，如「查看薪酬明细」 */
    String value();

    /** 业务模块，如 auth / emp / cmp */
    String module() default "";
}
