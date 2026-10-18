package com.hris.common.security;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 权限点校验注解（「模块:资源:动作」，docs/08 §2）。标注受保护端点，由 {@link PermAspect}
 * 校验当前用户 perms 是否包含该权限点；缺失则抛 20403。
 */
@Documented
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RequirePerm {

    /** 权限点编码，如 org:headcount:write */
    String value();
}
